package ru.mephi.hotelbooking.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.mephi.hotelbooking.booking.client.HotelCallFailedException;
import ru.mephi.hotelbooking.booking.client.HotelCallRejectedException;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.client.RoomView;
import ru.mephi.hotelbooking.booking.dto.BookingResponse;
import ru.mephi.hotelbooking.booking.dto.CreateBookingRequest;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.exception.BadRequestException;
import ru.mephi.hotelbooking.booking.exception.BookingFailedException;
import ru.mephi.hotelbooking.booking.exception.ForbiddenOperationException;
import ru.mephi.hotelbooking.booking.mapper.BookingMapper;
import ru.mephi.hotelbooking.booking.security.CurrentUser;

/**
 * Оркестрирует сагу бронирования (последовательность локальных транзакций, распределённой транзакции нет):
 * <ol>
 *     <li>локальная транзакция: бронирование создаётся и фиксируется в статусе PENDING;</li>
 *     <li>удалённый вызов: сервис отелей подтверждает доступность и временно блокирует номер (тайм-ауты,
 *     ограниченные повторы с экспоненциальной паузой, идемпотентность по requestId);</li>
 *     <li>локальная транзакция: при успехе PENDING -> CONFIRMED; при ошибке или тайм-ауте выполняется компенсация:
 *     блокировка снимается в сервисе отелей, а бронирование переходит PENDING -> CANCELLED.</li>
 * </ol>
 * Каждый шаг логируется с bookingId, который также передаётся сервису отелей как заголовок корреляции.
 * <p>
 * Этот класс намеренно НЕ транзакционный.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSagaService {

    private final BookingTxService tx;
    private final HotelClient hotelClient;
    private final BookingMapper bookingMapper;

    /**
     * @param replayed true, если запрос уже обрабатывался ранее и возвращён сохранённый результат
     */
    public record CreationResult(BookingResponse booking, boolean replayed) {
    }

    public CreationResult create(CurrentUser user, CreateBookingRequest request) {
        validate(request);
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId().trim();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();

        // идемпотентность: повторно доставленный запрос возвращает сохранённый результат и ничего не меняет
        Optional<Booking> existing = tx.findByRequest(user.id(), requestId);
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        List<Long> candidates = request.autoSelect()
                ? selectCandidates(start, end)
                : List.of(request.roomId());

        Booking pending;
        try {
            pending = tx.createPending(user.id(), requestId, candidates.get(0), start, end);
        } catch (DataIntegrityViolationException | BookingFailedException race) {
            // Две доставки одного запроса столкнулись: победила другая (её PENDING-запись уже видна, из-за чего
            // и предварительная проверка пересечений не прошла). Возвращаем её результат вместо ошибки.
            Optional<Booking> winner = tx.findByRequest(user.id(), requestId);
            if (winner.isPresent()) {
                return replay(winner.get());
            }
            throw race;
        }

        MDC.put("bookingId", String.valueOf(pending.getId()));
        MDC.put("requestId", requestId);
        try {
            log.info("bookingId={} создано в статусе PENDING: userId={} roomId={} период=[{}, {}) requestId={} autoSelect={}",
                    pending.getId(), user.id(), pending.getRoomId(), start, end, requestId, request.autoSelect());
            return runSaga(pending, candidates);
        } finally {
            MDC.remove("bookingId");
            MDC.remove("requestId");
        }
    }

    /**
     * Отменяет бронирование. Подтверждённое бронирование сначала освобождает номер в сервисе отелей; если сервис
     * недоступен, бронирование остаётся CONFIRMED, а клиент получает 503: так два сервиса не расходятся.
     */
    public BookingResponse cancel(CurrentUser user, Long bookingId) {
        Booking booking = tx.get(bookingId);
        if (!user.canAccessBookingOf(booking.getUser().getId())) {
            throw new ForbiddenOperationException("Бронирование " + bookingId + " принадлежит другому пользователю");
        }
        MDC.put("bookingId", String.valueOf(bookingId));
        MDC.put("requestId", booking.getRequestId());
        try {
            switch (booking.getStatus()) {
                case CANCELLED -> log.info("bookingId={} запрошена отмена: бронирование уже CANCELLED, делать нечего", bookingId);
                case PENDING -> {
                    log.info("bookingId={} запрошена отмена в статусе PENDING", bookingId);
                    compensate(booking, "Отменено пользователем");
                }
                case CONFIRMED -> {
                    log.info("bookingId={} запрошена отмена в статусе CONFIRMED", bookingId);
                    if (!releaseSlot(booking)) {
                        throw new BookingFailedException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Сервис отелей недоступен: бронирование не отменено, повторите позже",
                                bookingId, BookingStatus.CONFIRMED);
                    }
                    tx.transition(bookingId, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, "Отменено пользователем");
                }
            }
            return bookingMapper.toResponse(tx.get(bookingId));
        } finally {
            MDC.remove("bookingId");
            MDC.remove("requestId");
        }
    }

    /**
     * Компенсация: снимает блокировку в сервисе отелей (идемпотентно, с повторами) и переводит бронирование
     * PENDING -> CANCELLED. Если release не удался, бронирование помечается, и фоновая задача повторит снятие.
     */
    public void compensate(Booking booking, String reason) {
        boolean released = releaseSlot(booking);
        tx.setReleasePending(booking.getId(), !released);
        boolean cancelled = tx.transition(booking.getId(), BookingStatus.PENDING, BookingStatus.CANCELLED, reason);
        log.info("bookingId={} компенсация завершена: блокировка снята={} отменено={} причина={}",
                booking.getId(), released, cancelled, reason);
    }

    /**
     * Снимает временную блокировку текущего номера бронирования в сервисе отелей.
     *
     * @return true, если блокировки больше нет (снята или снимать было нечего)
     */
    public boolean releaseSlot(Booking booking) {
        String hotelRequestId = booking.hotelRequestId();
        try {
            hotelClient.release(booking.getRoomId(), hotelRequestId, booking.getId());
            log.info("bookingId={} компенсация: блокировка снята roomId={} requestId={}",
                    booking.getId(), booking.getRoomId(), hotelRequestId);
            return true;
        } catch (HotelCallRejectedException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND.value()) {
                log.info("bookingId={} компенсация: номера {} больше нет, снимать нечего",
                        booking.getId(), booking.getRoomId());
                return true;
            }
            log.error("bookingId={} компенсация НЕ ВЫПОЛНЕНА: release roomId={} requestId={} отклонён со статусом {}",
                    booking.getId(), booking.getRoomId(), hotelRequestId, ex.getStatus());
            return false;
        } catch (HotelCallFailedException ex) {
            log.error("bookingId={} компенсация НЕ ВЫПОЛНЕНА: release roomId={} requestId={} не доставлен: {}",
                    booking.getId(), booking.getRoomId(), hotelRequestId, ex.getMessage());
            return false;
        }
    }

    private CreationResult runSaga(Booking pending, List<Long> candidates) {
        Long bookingId = pending.getId();
        LocalDate start = pending.getStartDate();
        LocalDate end = pending.getEndDate();
        int lastRejectionStatus = 0;

        for (int i = 0; i < candidates.size(); i++) {
            Long roomId = candidates.get(i);
            Booking current = pending;
            if (i > 0) {
                if (tx.isRoomBusy(roomId, start, end)) {
                    continue;
                }
                if (!tx.reassignRoom(bookingId, roomId)) {
                    log.warn("bookingId={} больше не в статусе PENDING, сага остановлена", bookingId);
                    return new CreationResult(bookingMapper.toResponse(tx.get(bookingId)), false);
                }
                log.info("bookingId={} пробуем следующий номер roomId={}", bookingId, roomId);
                current = tx.get(bookingId);
            }

            try {
                hotelClient.confirmAvailability(roomId, current.hotelRequestId(), bookingId, start, end);
            } catch (HotelCallRejectedException ex) {
                // однозначный ответ: ничего не заблокировано, компенсировать нечего
                lastRejectionStatus = ex.getStatus();
                log.info("bookingId={} сервис отелей отклонил roomId={} со статусом {}: блокировка не создана, release не нужен",
                        bookingId, roomId, ex.getStatus());
                if (ex.getStatus() == HttpStatus.CONFLICT.value() && i < candidates.size() - 1) {
                    continue;
                }
                break;
            } catch (HotelCallFailedException ex) {
                log.error("bookingId={} confirm-availability не удался после всех попыток (roomId={}): {}. Запускаем компенсацию",
                        bookingId, roomId, ex.getMessage());
                compensate(current, "Ошибка или тайм-аут сервиса отелей: " + ex.getMessage());
                throw new BookingFailedException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Сервис отелей недоступен, бронирование отменено. Повторите позже с новым requestId "
                                + "(тот же requestId вернёт это отменённое бронирование)",
                        bookingId, BookingStatus.CANCELLED);
            }

            if (tx.transition(bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED, null)) {
                log.info("bookingId={} подтверждено: roomId={} период=[{}, {})", bookingId, roomId, start, end);
                return new CreationResult(bookingMapper.toResponse(tx.get(bookingId)), false);
            }
            // Бронирование было отменено параллельно, пока сервис отелей блокировал номер: откатываем блокировку.
            log.warn("bookingId={} изменено параллельно во время подтверждения, снимаем блокировку", bookingId);
            boolean released = releaseSlot(current);
            tx.setReleasePending(bookingId, !released);
            return new CreationResult(bookingMapper.toResponse(tx.get(bookingId)), false);
        }

        return rejected(bookingId, lastRejectionStatus);
    }

    private CreationResult rejected(Long bookingId, int lastRejectionStatus) {
        HttpStatus status;
        String message;
        switch (lastRejectionStatus) {
            case 404 -> {
                status = HttpStatus.NOT_FOUND;
                message = "Номер не найден";
            }
            case 0, 409 -> {
                status = HttpStatus.CONFLICT;
                message = "Номер недоступен на запрошенные даты";
            }
            default -> {
                status = HttpStatus.BAD_GATEWAY;
                message = "Сервис отелей отклонил запрос со статусом " + lastRejectionStatus;
            }
        }
        tx.transition(bookingId, BookingStatus.PENDING, BookingStatus.CANCELLED, message);
        throw new BookingFailedException(status, message, bookingId, BookingStatus.CANCELLED);
    }

    private List<Long> selectCandidates(LocalDate start, LocalDate end) {
        List<RoomView> recommended;
        try {
            recommended = hotelClient.recommendRooms(start, end);
        } catch (HotelCallRejectedException ex) {
            throw new BookingFailedException(HttpStatus.BAD_GATEWAY,
                    "Сервис отелей отклонил поиск номеров со статусом " + ex.getStatus(), null, null);
        } catch (HotelCallFailedException ex) {
            throw new BookingFailedException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Сервис отелей недоступен, автоматический подбор номера невозможен", null, null);
        }
        List<Long> candidates = recommended.stream()
                .map(RoomView::id)
                .filter(id -> !tx.isRoomBusy(id, start, end))
                .toList();
        if (candidates.isEmpty()) {
            throw new BookingFailedException(HttpStatus.CONFLICT,
                    "Нет свободных номеров на запрошенные даты", null, null);
        }
        log.info("автоподбор: кандидаты по возрастанию times_booked на период [{}, {}): {}", start, end, candidates);
        return candidates;
    }

    private CreationResult replay(Booking existing) {
        log.info("bookingId={} повторная доставка requestId={}: возвращаем сохранённый результат, статус={}",
                existing.getId(), existing.getRequestId(), existing.getStatus());
        return new CreationResult(bookingMapper.toResponse(existing), true);
    }

    private void validate(CreateBookingRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BadRequestException("Дата выезда (endDate) должна быть позже даты заезда (startDate)");
        }
        if (!request.autoSelect() && request.roomId() == null) {
            throw new BadRequestException("Поле roomId обязательно, если autoSelect=false");
        }
    }
}

package ru.mephi.hotelbooking.booking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.exception.BookingFailedException;
import ru.mephi.hotelbooking.booking.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;

/**
 * Локальные транзакции саги бронирования. Каждый метод - одна короткая транзакция в базе Booking Service.
 * Оркестратор ({@link BookingSagaService}) намеренно не транзакционный: удалённые вызовы сервиса отелей
 * никогда не выполняются внутри транзакции БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingTxService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    /** Локальная транзакция 1: сохраняет бронирование в статусе PENDING. */
    @Transactional
    public Booking createPending(Long userId, String requestId, Long roomId, LocalDate startDate, LocalDate endDate) {
        if (isRoomBusy(roomId, startDate, endDate)) {
            throw new BookingFailedException(HttpStatus.CONFLICT,
                    "Номер " + roomId + " уже забронирован на запрошенные даты", null, null);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь " + userId + " не найден"));
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(roomId);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setStatus(BookingStatus.PENDING);
        booking.setRequestId(requestId);
        return bookingRepository.saveAndFlush(booking);
    }

    /** Быстрая предварительная проверка; окончательное решение при конкурентных попытках принимает сервис отелей. */
    @Transactional(readOnly = true)
    public boolean isRoomBusy(Long roomId, LocalDate startDate, LocalDate endDate) {
        return bookingRepository.existsByRoomIdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
                roomId, BookingStatus.ACTIVE, endDate, startDate);
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findByRequest(Long userId, String requestId) {
        return bookingRepository.findByUserIdAndRequestId(userId, requestId);
    }

    @Transactional(readOnly = true)
    public Booking get(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Бронирование " + id + " не найдено"));
    }

    /**
     * Переход состояния по принципу compare-and-set в отдельной транзакции.
     *
     * @return true, если бронирование было в статусе {@code from} и переведено дальше
     */
    @Transactional
    public boolean transition(Long bookingId, BookingStatus from, BookingStatus to, String reason) {
        boolean moved = bookingRepository.transition(bookingId, from, to, reason, Instant.now()) == 1;
        if (moved) {
            log.info("bookingId={} статус {} -> {}{}", bookingId, from, to, reason == null ? "" : " (" + reason + ")");
        } else {
            log.info("bookingId={} переход {} -> {} пропущен: бронирование уже не в статусе {}",
                    bookingId, from, to, from);
        }
        return moved;
    }

    /** Переносит PENDING-бронирование на следующий номер-кандидат (автоподбор). */
    @Transactional
    public boolean reassignRoom(Long bookingId, Long roomId) {
        return bookingRepository.reassignRoom(bookingId, roomId, Instant.now()) == 1;
    }

    @Transactional
    public void setReleasePending(Long bookingId, boolean pending) {
        bookingRepository.setReleasePending(bookingId, pending, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<Booking> findStalePending(Instant olderThan) {
        return bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, olderThan);
    }

    @Transactional(readOnly = true)
    public List<Booking> findCancelledWithPendingRelease() {
        return bookingRepository.findByStatusAndReleasePendingTrue(BookingStatus.CANCELLED);
    }
}

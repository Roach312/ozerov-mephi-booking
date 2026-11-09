package ru.mephi.hotelbooking.hotel.service;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityRequest;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityResponse;
import ru.mephi.hotelbooking.hotel.dto.ReleaseRequest;
import ru.mephi.hotelbooking.hotel.dto.ReleaseResponse;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;
import ru.mephi.hotelbooking.hotel.exception.BadRequestException;
import ru.mephi.hotelbooking.hotel.exception.ConflictException;
import ru.mephi.hotelbooking.hotel.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.hotel.mapper.SlotMapper;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomSlotRepository;

/**
 * Сторона-участник двухшагового согласования бронирования (на каждый вызов своя локальная транзакция).
 * <ul>
 *     <li>{@link #confirm}: временно блокирует номер на период; идемпотентно по requestId.</li>
 *     <li>{@link #release}: компенсирующее действие; идемпотентно по requestId.</li>
 * </ul>
 * Все операции с одним номером сериализуются пессимистичной блокировкой строки, поэтому одновременные попытки
 * заблокировать один номер на пересекающиеся даты решаются здесь: побеждает ровно одна, остальные получают 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomAvailabilityService {

    private final RoomRepository roomRepository;
    private final RoomSlotRepository slotRepository;
    private final SlotMapper slotMapper;

    @Transactional
    public AvailabilityResponse confirm(Long roomId, AvailabilityRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BadRequestException("Дата выезда (endDate) должна быть позже даты заезда (startDate)");
        }
        Room room = lockRoom(roomId);

        Optional<RoomSlot> existing = slotRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            return replayConfirm(roomId, existing.get(), request);
        }

        if (!room.isAvailable()) {
            log.info("confirm-availability ОТКЛОНЁН: roomId={} bookingId={} requestId={} причина=номер операционно недоступен",
                    roomId, request.bookingId(), request.requestId());
            throw new ConflictException("Номер " + roomId + " недоступен");
        }
        boolean overlaps = slotRepository.existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                roomId, SlotStatus.HELD, request.endDate(), request.startDate());
        if (overlaps) {
            log.info("confirm-availability ОТКЛОНЁН: roomId={} bookingId={} requestId={} причина=пересечение с другим слотом на период [{}, {})",
                    roomId, request.bookingId(), request.requestId(), request.startDate(), request.endDate());
            throw new ConflictException("Номер " + roomId + " уже зарезервирован на запрошенные даты");
        }

        RoomSlot slot = new RoomSlot();
        slot.setRoom(room);
        slot.setRequestId(request.requestId());
        slot.setBookingId(request.bookingId());
        slot.setStartDate(request.startDate());
        slot.setEndDate(request.endDate());
        slot.setStatus(SlotStatus.HELD);
        slot = slotRepository.saveAndFlush(slot);
        room.setTimesBooked(room.getTimesBooked() + 1);

        log.info("confirm-availability ВЫПОЛНЕН: слот HELD roomId={} bookingId={} requestId={} период=[{}, {}) timesBooked={}",
                roomId, request.bookingId(), request.requestId(), request.startDate(), request.endDate(), room.getTimesBooked());
        return slotMapper.toAvailability(slot);
    }

    @Transactional
    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        Room room = lockRoom(roomId);

        Optional<RoomSlot> existing = slotRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            RoomSlot slot = existing.get();
            assertSameRoom(roomId, slot);
            if (slot.getStatus() == SlotStatus.HELD) {
                slot.setStatus(SlotStatus.RELEASED);
                slot.setReleasedAt(Instant.now());
                room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
                log.info("release ВЫПОЛНЕН: слот RELEASED roomId={} bookingId={} requestId={} timesBooked={}",
                        roomId, request.bookingId(), request.requestId(), room.getTimesBooked());
                return new ReleaseResponse(request.requestId(), roomId, SlotStatus.RELEASED, true);
            }
            log.info("release повтор: слот уже RELEASED roomId={} bookingId={} requestId={}",
                    roomId, request.bookingId(), request.requestId());
            return new ReleaseResponse(request.requestId(), roomId, SlotStatus.RELEASED, false);
        }

        // С таким requestId ничего не блокировалось (пока). Записываем "надгробие": запоздавший confirm с тем же
        // requestId (например, запрос, по которому у вызывающей стороны истёк тайм-аут, но который ещё в пути)
        // будет отклонён и не оставит "висящую" блокировку.
        RoomSlot tombstone = new RoomSlot();
        tombstone.setRoom(room);
        tombstone.setRequestId(request.requestId());
        tombstone.setBookingId(request.bookingId());
        tombstone.setStatus(SlotStatus.RELEASED);
        tombstone.setReleasedAt(Instant.now());
        slotRepository.saveAndFlush(tombstone);
        log.info("release: снимать нечего, записано надгробие roomId={} bookingId={} requestId={}",
                roomId, request.bookingId(), request.requestId());
        return new ReleaseResponse(request.requestId(), roomId, SlotStatus.RELEASED, false);
    }

    private AvailabilityResponse replayConfirm(Long roomId, RoomSlot slot, AvailabilityRequest request) {
        assertSameRoom(roomId, slot);
        if (slot.getStatus() == SlotStatus.RELEASED) {
            log.info("confirm-availability ОТКЛОНЁН: roomId={} bookingId={} requestId={} причина=блокировка с этим requestId уже снята",
                    roomId, request.bookingId(), request.requestId());
            throw new ConflictException("Блокировка для requestId " + request.requestId() + " уже снята");
        }
        if (!request.startDate().equals(slot.getStartDate()) || !request.endDate().equals(slot.getEndDate())) {
            throw new ConflictException("requestId " + request.requestId() + " уже использован для другого периода");
        }
        log.info("confirm-availability повтор: слот уже HELD, состояние не меняется roomId={} bookingId={} requestId={}",
                roomId, request.bookingId(), request.requestId());
        return slotMapper.toAvailability(slot);
    }

    private void assertSameRoom(Long roomId, RoomSlot slot) {
        if (!slot.getRoom().getId().equals(roomId)) {
            throw new ConflictException("requestId " + slot.getRequestId() + " уже использован для другого номера");
        }
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Номер " + roomId + " не найден"));
    }
}

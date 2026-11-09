package ru.mephi.hotelbooking.hotel.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.hotel.dto.HotelLoadRow;
import ru.mephi.hotelbooking.hotel.dto.RoomLoadResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;
import ru.mephi.hotelbooking.hotel.exception.BadRequestException;
import ru.mephi.hotelbooking.hotel.exception.ConflictException;
import ru.mephi.hotelbooking.hotel.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.hotel.mapper.RoomMapper;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomSlotRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    /** Порядок для равномерной загрузки: сначала наименее бронируемые номера, при равенстве по id. */
    private static final Sort RECOMMEND_ORDER = Sort.by(Sort.Order.asc("timesBooked"), Sort.Order.asc("id"));
    private static final Sort NATURAL_ORDER = Sort.by("id");

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomSlotRepository slotRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponse create(RoomRequest request) {
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumber(hotel.getId(), request.number())) {
            throw new ConflictException("Номер " + request.number() + " уже существует в отеле " + hotel.getId());
        }
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        room.setAvailable(request.available() == null || request.available());
        room = roomRepository.save(room);
        log.info("Номер создан: id={}, hotelId={}, номер={}", room.getId(), hotel.getId(), room.getNumber());
        return roomMapper.toResponse(room);
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {
        return roomMapper.toResponse(find(id));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = find(id);
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumberAndIdNot(hotel.getId(), request.number(), id)) {
            throw new ConflictException("Номер " + request.number() + " уже существует в отеле " + hotel.getId());
        }
        room.setHotel(hotel);
        room.setNumber(request.number());
        if (request.available() != null) {
            room.setAvailable(request.available());
        }
        log.info("Номер изменён: id={}", id);
        return roomMapper.toResponse(room);
    }

    @Transactional
    public void delete(Long id) {
        Room room = find(id);
        if (slotRepository.existsByRoomIdAndStatus(id, SlotStatus.HELD)) {
            throw new ConflictException("У номера " + id + " есть активные резервы, удалить его нельзя");
        }
        slotRepository.deleteByRoomId(id);
        roomRepository.delete(room);
        log.info("Номер удалён: id={}", id);
    }

    /** Все операционно доступные номера; с датами - только свободные на период. Без особой сортировки. */
    @Transactional(readOnly = true)
    public List<RoomResponse> findFree(LocalDate startDate, LocalDate endDate) {
        return query(startDate, endDate, NATURAL_ORDER);
    }

    /** Те же свободные номера, отсортированные по возрастанию times_booked, затем по id. */
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(LocalDate startDate, LocalDate endDate) {
        return query(startDate, endDate, RECOMMEND_ORDER);
    }

    /** Статистика загруженности: агрегаты по отелям (JPQL GROUP BY) и разбивка по номерам. */
    @Transactional(readOnly = true)
    public List<RoomLoadResponse> loadStatistics() {
        Map<Long, List<RoomResponse>> roomsByHotel = roomRepository.findAll(NATURAL_ORDER).stream()
                .map(roomMapper::toResponse)
                .sorted(Comparator.comparingInt(RoomResponse::timesBooked).reversed().thenComparing(RoomResponse::id))
                .collect(Collectors.groupingBy(RoomResponse::hotelId));

        return roomRepository.aggregateLoadByHotel().stream()
                .map((HotelLoadRow row) -> new RoomLoadResponse(
                        row.hotelId(),
                        row.hotelName(),
                        row.roomCount(),
                        row.totalTimesBooked(),
                        row.averageTimesBooked(),
                        roomsByHotel.getOrDefault(row.hotelId(), List.of())))
                .toList();
    }

    private List<RoomResponse> query(LocalDate startDate, LocalDate endDate, Sort sort) {
        List<Room> rooms;
        if (startDate == null && endDate == null) {
            rooms = roomRepository.findByAvailableTrue(sort);
        } else {
            if (startDate == null || endDate == null) {
                throw new BadRequestException("Параметры startDate и endDate нужно указывать вместе");
            }
            if (!endDate.isAfter(startDate)) {
                throw new BadRequestException("Дата выезда (endDate) должна быть позже даты заезда (startDate)");
            }
            rooms = roomRepository.findAvailableForPeriod(startDate, endDate, sort);
        }
        return rooms.stream().map(roomMapper::toResponse).toList();
    }

    private Room find(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Номер " + id + " не найден"));
    }

    private Hotel findHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Отель " + hotelId + " не найден"));
    }
}

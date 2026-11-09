package ru.mephi.hotelbooking.hotel.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.exception.ConflictException;
import ru.mephi.hotelbooking.hotel.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.hotel.mapper.HotelMapper;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

    @Transactional
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        log.info("Отель создан: id={}, название={}", hotel.getId(), hotel.getName());
        return hotelMapper.toResponse(hotel);
    }

    @Transactional(readOnly = true)
    public List<HotelResponse> findAll() {
        return hotelRepository.findAll(Sort.by("id")).stream().map(hotelMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HotelResponse get(Long id) {
        return hotelMapper.toResponse(find(id));
    }

    @Transactional
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = find(id);
        hotelMapper.update(request, hotel);
        log.info("Отель изменён: id={}", id);
        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    public void delete(Long id) {
        Hotel hotel = find(id);
        if (roomRepository.existsByHotelId(id)) {
            throw new ConflictException("В отеле " + id + " ещё есть номера: сначала удалите номера");
        }
        hotelRepository.delete(hotel);
        log.info("Отель удалён: id={}", id);
    }

    private Hotel find(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Отель " + id + " не найден"));
    }
}

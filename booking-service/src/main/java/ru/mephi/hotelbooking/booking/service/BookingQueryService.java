package ru.mephi.hotelbooking.booking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.dto.BookingResponse;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.exception.ForbiddenOperationException;
import ru.mephi.hotelbooking.booking.exception.ResourceNotFoundException;
import ru.mephi.hotelbooking.booking.mapper.BookingMapper;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    /** История бронирований вызывающего, новые первыми. */
    @Transactional(readOnly = true)
    public List<BookingResponse> history(CurrentUser user) {
        return bookingRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.id()).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(CurrentUser user, Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Бронирование " + id + " не найдено"));
        if (!user.canAccessBookingOf(booking.getUser().getId())) {
            throw new ForbiddenOperationException("Бронирование " + id + " принадлежит другому пользователю");
        }
        return bookingMapper.toResponse(booking);
    }
}

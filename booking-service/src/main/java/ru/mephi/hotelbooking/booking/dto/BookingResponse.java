package ru.mephi.hotelbooking.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;

public record BookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        Instant createdAt,
        String requestId,
        String failureReason) {
}

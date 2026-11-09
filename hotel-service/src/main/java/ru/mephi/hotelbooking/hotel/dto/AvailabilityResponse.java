package ru.mephi.hotelbooking.hotel.dto;

import java.time.LocalDate;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

public record AvailabilityResponse(
        String requestId,
        Long roomId,
        Long bookingId,
        LocalDate startDate,
        LocalDate endDate,
        SlotStatus status) {
}

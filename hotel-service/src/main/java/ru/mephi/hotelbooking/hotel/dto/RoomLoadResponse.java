package ru.mephi.hotelbooking.hotel.dto;

import java.util.List;

/** Статистика загруженности одного отеля. */
public record RoomLoadResponse(
        Long hotelId,
        String hotelName,
        long roomCount,
        long totalTimesBooked,
        double averageTimesBooked,
        List<RoomResponse> rooms) {
}

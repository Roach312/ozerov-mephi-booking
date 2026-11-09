package ru.mephi.hotelbooking.hotel.dto;

/** Строка агрегации, создаваемая конструкторным выражением JPQL (см. RoomRepository#aggregateLoadByHotel). */
public record HotelLoadRow(
        Long hotelId,
        String hotelName,
        Long roomCount,
        Long totalTimesBooked,
        Double averageTimesBooked) {
}

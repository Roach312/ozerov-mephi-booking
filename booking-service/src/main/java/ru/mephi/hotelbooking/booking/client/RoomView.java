package ru.mephi.hotelbooking.booking.client;

/** Номер в представлении сервиса отелей (только поля, нужные Booking Service). */
public record RoomView(Long id, Long hotelId, String number, boolean available, int timesBooked) {
}

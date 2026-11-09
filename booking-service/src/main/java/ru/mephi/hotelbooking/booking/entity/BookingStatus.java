package ru.mephi.hotelbooking.booking.entity;

import java.util.List;

/**
 * Жизненный цикл бронирования: PENDING -> CONFIRMED (доступность подтверждена) и PENDING -> CANCELLED (отказ,
 * ошибка, тайм-аут: выполняется компенсация). Подтверждённое бронирование пользователь может отменить:
 * CONFIRMED -> CANCELLED.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    /** Статусы бронирований, которые занимают номер. */
    public static final List<BookingStatus> ACTIVE = List.of(PENDING, CONFIRMED);
}

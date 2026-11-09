package ru.mephi.hotelbooking.booking.client;

import lombok.Getter;

/**
 * Сервис отелей ответил однозначным статусом 4xx (например, 409: номер занят). Вызов не повторяется,
 * блокировка не создана.
 */
@Getter
public class HotelCallRejectedException extends RuntimeException {

    private final int status;

    public HotelCallRejectedException(int status, String message) {
        super(message);
        this.status = status;
    }
}

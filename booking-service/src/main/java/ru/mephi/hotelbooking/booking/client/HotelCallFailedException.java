package ru.mephi.hotelbooking.booking.client;

/**
 * Вызов сервиса отелей завершился с неопределённым исходом: ошибка ввода-вывода, тайм-аут, 5xx или нет доступных
 * экземпляров. Только это исключение повторяется; если оно вышло из клиента, попытки исчерпаны.
 */
public class HotelCallFailedException extends RuntimeException {

    public HotelCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

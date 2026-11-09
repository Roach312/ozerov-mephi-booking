package ru.mephi.hotelbooking.booking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;

/**
 * Бронирование не удалось завершить. Несёт HTTP-статус ответа и, если запись бронирования уже создана, её
 * идентификатор и итоговый статус (чтобы клиент мог связать отказ с бронированием).
 * <ul>
 *     <li>409: номер занят на эти даты (в том числе проигравший в гонке одновременных запросов)</li>
 *     <li>404: номер не существует</li>
 *     <li>503: сервис отелей не ответил или превышен тайм-аут, компенсация выполнена</li>
 *     <li>502: сервис отелей неожиданно отклонил вызов</li>
 * </ul>
 */
@Getter
public class BookingFailedException extends RuntimeException {

    private final HttpStatus status;
    private final Long bookingId;
    private final BookingStatus bookingStatus;

    public BookingFailedException(HttpStatus status, String message, Long bookingId, BookingStatus bookingStatus) {
        super(message);
        this.status = status;
        this.bookingId = bookingId;
        this.bookingStatus = bookingStatus;
    }
}

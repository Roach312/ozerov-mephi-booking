package ru.mephi.hotelbooking.booking.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import ru.mephi.hotelbooking.booking.security.JwtService;

/**
 * Удалённые вызовы сервиса отелей. У каждого вызова есть тайм-ауты (см. {@link HotelClientConfig}), ограниченное
 * число попыток с экспоненциальной паузой и сквозные идентификаторы (requestId делает удалённую операцию
 * идемпотентной, поэтому повторы и повторные доставки не меняют состояние дважды).
 */
@Slf4j
@Component
public class HotelClient {

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;
    private final JwtService jwtService;
    private final int maxAttempts;

    public HotelClient(RestClient hotelRestClient, RetryTemplate hotelRetryTemplate, JwtService jwtService,
                       HotelClientProperties properties) {
        this.restClient = hotelRestClient;
        this.retryTemplate = hotelRetryTemplate;
        this.jwtService = jwtService;
        this.maxAttempts = properties.getMaxAttempts();
    }

    /**
     * Шаг 2 двухшагового согласования: просит сервис отелей подтвердить доступность и временно заблокировать
     * номер на период [startDate, endDate).
     *
     * @throws HotelCallRejectedException однозначный отказ (409 номер занят, 404 нет такого номера и т.п.)
     * @throws HotelCallFailedException   тайм-аут, ошибка ввода-вывода или 5xx после всех попыток
     */
    public void confirmAvailability(Long roomId, String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {
        String operation = "confirm-availability roomId=" + roomId + " requestId=" + requestId;
        call(operation, bookingId, requestId, () -> restClient.post()
                .uri("/api/rooms/{id}/confirm-availability", roomId)
                .headers(headers -> addHeaders(headers, bookingId, requestId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmPayload(requestId, bookingId, startDate, endDate))
                .retrieve()
                .toBodilessEntity());
    }

    /** Компенсирующее действие: снимает временную блокировку, созданную с тем же requestId. Идемпотентно. */
    public void release(Long roomId, String requestId, Long bookingId) {
        String operation = "release roomId=" + roomId + " requestId=" + requestId;
        call(operation, bookingId, requestId, () -> restClient.post()
                .uri("/api/rooms/{id}/release", roomId)
                .headers(headers -> addHeaders(headers, bookingId, requestId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReleasePayload(requestId, bookingId))
                .retrieve()
                .toBodilessEntity());
    }

    /** Свободные на период номера по возрастанию times_booked (затем по id): порядок равномерной загрузки. */
    public List<RoomView> recommendRooms(LocalDate startDate, LocalDate endDate) {
        String operation = "recommend startDate=" + startDate + " endDate=" + endDate;
        List<RoomView> rooms = call(operation, null, null, () -> restClient.get()
                .uri("/api/rooms/recommend?startDate={start}&endDate={end}", startDate.toString(), endDate.toString())
                .headers(headers -> addHeaders(headers, null, null))
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomView>>() {
                }));
        return rooms == null ? List.of() : rooms;
    }

    private <T> T call(String operation, Long bookingId, String requestId, Supplier<T> request) {
        return retryTemplate.execute(context -> {
            int attempt = context.getRetryCount() + 1;
            try {
                T result = request.get();
                log.info("вызов сервиса отелей выполнен: {} bookingId={} попытка={}/{}", operation, bookingId, attempt, maxAttempts);
                return result;
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode().is4xxClientError()) {
                    log.info("вызов сервиса отелей ОТКЛОНЁН: {} bookingId={} статус={} тело={}",
                            operation, bookingId, ex.getStatusCode().value(), abbreviate(ex.getResponseBodyAsString()));
                    throw new HotelCallRejectedException(ex.getStatusCode().value(),
                            "Сервис отелей ответил " + ex.getStatusCode().value() + " на " + operation);
                }
                log.warn("вызов сервиса отелей НЕ УДАЛСЯ: {} bookingId={} попытка={}/{} статус={}",
                        operation, bookingId, attempt, maxAttempts, ex.getStatusCode().value());
                throw new HotelCallFailedException("Сервис отелей ответил " + ex.getStatusCode().value()
                        + " на " + operation, ex);
            } catch (HotelCallRejectedException | HotelCallFailedException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                // ошибка ввода-вывода, тайм-аут, нет доступных экземпляров, нечитаемый ответ
                log.warn("вызов сервиса отелей НЕ УДАЛСЯ: {} bookingId={} попытка={}/{} причина={}",
                        operation, bookingId, attempt, maxAttempts, describe(ex));
                throw new HotelCallFailedException("Вызов сервиса отелей не удался (" + classify(ex) + "): " + operation, ex);
            }
        });
    }

    private void addHeaders(HttpHeaders headers, Long bookingId, String requestId) {
        headers.setBearerAuth(jwtService.generateServiceToken());
        if (bookingId != null) {
            headers.set("X-Booking-Id", String.valueOf(bookingId));
        }
        if (requestId != null) {
            headers.set("X-Request-Id", requestId);
        }
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
    }

    /** Техническая причина для лога: класс и сообщение корневого исключения. */
    private static String describe(Throwable ex) {
        Throwable root = rootCause(ex);
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }

    /** Причина сбоя по-русски для сообщений об ошибке, которые видит клиент. */
    private static String classify(Throwable ex) {
        Throwable root = rootCause(ex);
        if (root instanceof HttpTimeoutException || root instanceof SocketTimeoutException) {
            return "тайм-аут ответа";
        }
        if (root instanceof ConnectException) {
            return "не удалось установить соединение";
        }
        if (root instanceof IllegalStateException && String.valueOf(root.getMessage()).contains("No instances available")) {
            return "нет доступных экземпляров сервиса";
        }
        return "ошибка " + root.getClass().getSimpleName();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    record ConfirmPayload(String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {
    }

    record ReleasePayload(String requestId, Long bookingId) {
    }
}

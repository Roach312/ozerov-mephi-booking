package ru.mephi.hotelbooking.booking;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.Role;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.service.StaleBookingReaper;

/**
 * Распределённый сценарий бронирования против заглушки сервиса отелей: успех, ошибка удалённого сервиса, тайм-аут,
 * повторы, повторная доставка запроса, автоподбор номера, отмена и восстановление. Перехваченные логи доказывают,
 * что переходы состояний и результат компенсации прослеживаются по bookingId.
 */
@ExtendWith(OutputCaptureExtension.class)
class BookingSagaTest extends AbstractBookingTest {

    @Autowired
    private StaleBookingReaper reaper;

    // ------------------------------------------------------------------ успех

    @Test
    void successfulBookingMovesFromPendingToConfirmed(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, hotelOk());

        var result = postBooking(bearer(alice), 5L, false, "req-success")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(5))
                .andExpect(jsonPath("$.userId").value(alice.getId()))
                .andExpect(jsonPath("$.requestId").value("req-success"));
        long bookingId = readTree(result).get("id").asLong();

        assertThat(bookingRepository.findById(bookingId)).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CONFIRMED);

        // ровно один удалённый вызов, отправлены ключ идемпотентности и заголовки корреляции
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        LoggedRequest call = HOTEL.findAll(postRequestedFor(urlPathEqualTo(confirmPath(5)))).get(0);
        JsonNode sent = objectMapper.readTree(call.getBodyAsString());
        assertThat(sent.get("requestId").asText()).isEqualTo(bookingId + ":5");
        assertThat(sent.get("bookingId").asLong()).isEqualTo(bookingId);
        assertThat(sent.get("startDate").asText()).isEqualTo(start().toString());
        assertThat(sent.get("endDate").asText()).isEqualTo(end().toString());
        assertThat(call.getHeader("X-Booking-Id")).isEqualTo(String.valueOf(bookingId));
        assertThat(call.getHeader("X-Request-Id")).isEqualTo(bookingId + ":5");
        assertThat(call.getHeader("Authorization")).startsWith("Bearer ");

        // переходы состояний прослеживаются в логе по bookingId
        assertThat(output.getAll())
                .contains("bookingId=" + bookingId + " создано в статусе PENDING")
                .contains("bookingId=" + bookingId + " статус PENDING -> CONFIRMED");
    }

    // ------------------------------------------------------------------ ошибка удалённого сервиса, тайм-аут, компенсация

    @Test
    void remoteServerErrorExhaustsRetriesThenCompensates(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, serverError());
        stubRelease(5, hotelOk());

        var result = postBooking(bearer(alice), 5L, false, "req-error")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Сервис недоступен"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Сервис отелей недоступен, бронирование отменено")))
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.bookingId").isNumber());
        long bookingId = readTree(result).get("bookingId").asLong();

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.isReleasePending()).isFalse();

        // ограниченное число попыток, все с одним ключом идемпотентности
        HOTEL.verify(3, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        assertThat(sentRequestIds(confirmPath(5))).hasSize(3).containsOnly(bookingId + ":5");

        // компенсация: (возможно, созданная) блокировка снимается с тем же ключом
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(releasePath(5)))
                .withRequestBody(WireMock.matchingJsonPath(
                        "$.requestId", equalTo(bookingId + ":5"))));

        assertThat(output.getAll())
                .contains("bookingId=" + bookingId + " статус PENDING -> CANCELLED")
                .contains("bookingId=" + bookingId + " компенсация: блокировка снята")
                .contains("bookingId=" + bookingId + " компенсация завершена: блокировка снята=true отменено=true");
    }

    @Test
    void remoteTimeoutExhaustsRetriesThenCompensates(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        // ответ медленнее, чем тайм-аут чтения 500 мс
        stubConfirm(5, hotelOk().withFixedDelay(2_000));
        stubRelease(5, hotelOk());

        var result = postBooking(bearer(alice), 5L, false, "req-timeout")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
        long bookingId = readTree(result).get("bookingId").asLong();

        assertThat(bookingRepository.findById(bookingId)).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CANCELLED);
        HOTEL.verify(3, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(releasePath(5))));
        assertThat(output.getAll())
                .contains("bookingId=" + bookingId + " confirm-availability не удался после всех попыток")
                .contains("bookingId=" + bookingId + " компенсация завершена: блокировка снята=true отменено=true");
    }

    @Test
    void failedCompensationIsFlaggedAndRetriedInBackground(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, serverError());
        stubRelease(5, serverError());

        var result = postBooking(bearer(alice), 5L, false, "req-release-fails")
                .andExpect(status().isServiceUnavailable());
        long bookingId = readTree(result).get("bookingId").asLong();

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.isReleasePending()).isTrue();
        assertThat(output.getAll()).contains("bookingId=" + bookingId + " компенсация НЕ ВЫПОЛНЕНА");

        // сервис отелей восстановился: фоновая задача завершает компенсацию
        HOTEL.resetAll();
        stubRelease(5, hotelOk());
        reaper.recoverOnce();

        assertThat(bookingRepository.findById(bookingId).orElseThrow().isReleasePending()).isFalse();
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(releasePath(5))));
    }

    @Test
    void transientFailureIsRetriedWithTheSameKeyAndBookingIsConfirmed() throws Exception {
        User alice = createUser("alice", Role.USER);
        HOTEL.stubFor(WireMock.post(urlPathEqualTo(confirmPath(5))).inScenario("flaky")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("recovered"));
        HOTEL.stubFor(WireMock.post(urlPathEqualTo(confirmPath(5))).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(hotelOk()));

        var result = postBooking(bearer(alice), 5L, false, "req-flaky")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        long bookingId = readTree(result).get("id").asLong();

        assertThat(sentRequestIds(confirmPath(5))).hasSize(2).containsOnly(bookingId + ":5");
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(releasePath(5))));
    }

    @Test
    void roomTakenAnswerCancelsWithoutRetryAndWithoutRelease() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, hotelConflict());

        var result = postBooking(bearer(alice), 5L, false, "req-conflict")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Конфликт"))
                .andExpect(jsonPath("$.detail").value("Номер недоступен на запрошенные даты"))
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
        long bookingId = readTree(result).get("bookingId").asLong();

        assertThat(bookingRepository.findById(bookingId)).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CANCELLED);
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(releasePath(5))));
    }

    @Test
    void unknownRoomIsReportedAsNotFound() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(404, aResponse().withStatus(404).withBody("{\"status\":404}"));

        postBooking(bearer(alice), 404L, false, "req-unknown-room")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Номер не найден"))
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));

        assertThat(bookingRepository.findAll()).singleElement()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CANCELLED);
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(releasePath(404))));
    }

    // ------------------------------------------------------------------ идемпотентность и пересечения

    @Test
    void redeliveredRequestDoesNotCreateADuplicateOrCallHotelAgain(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, hotelOk());

        var first = postBooking(bearer(alice), 5L, false, "req-dup").andExpect(status().isCreated());
        long bookingId = readTree(first).get("id").asLong();

        postBooking(bearer(alice), 5L, false, "req-dup")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        postBooking(bearer(alice), 5L, false, "req-dup").andExpect(status().isOk());

        assertThat(bookingRepository.count()).isEqualTo(1);
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        assertThat(output.getAll()).contains("bookingId=" + bookingId + " повторная доставка requestId=req-dup");
    }

    @Test
    void sameRequestIdOfAnotherUserIsAnIndependentRequest() throws Exception {
        User alice = createUser("alice", Role.USER);
        User bob = createUser("bob", Role.USER);
        stubConfirm(5, hotelOk());
        stubConfirm(6, hotelOk());

        postBooking(bearer(alice), 5L, false, "shared-key").andExpect(status().isCreated());
        postBooking(bearer(bob), 6L, false, "shared-key").andExpect(status().isCreated());

        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    void overlappingBookingOfTheSameRoomIsRejectedWithoutCallingHotel() throws Exception {
        User alice = createUser("alice", Role.USER);
        User bob = createUser("bob", Role.USER);
        stubConfirm(5, hotelOk());

        postBooking(bearer(alice), 5L, false, "a-1").andExpect(status().isCreated());
        postBooking(bearer(bob), 5L, false, "b-1")
                .andExpect(status().isConflict());

        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(confirmPath(5))));
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    void cancelledBookingFreesTheRoomForTheSameDates() throws Exception {
        User alice = createUser("alice", Role.USER);
        User bob = createUser("bob", Role.USER);
        stubConfirm(5, hotelOk());
        stubRelease(5, hotelOk());

        var created = postBooking(bearer(alice), 5L, false, "a-1").andExpect(status().isCreated());
        long id = readTree(created).get("id").asLong();
        mockMvc.perform(delete("/booking/{id}", id).header(AUTHORIZATION, bearer(alice))).andExpect(status().isOk());

        postBooking(bearer(bob), 5L, false, "b-1").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ автоподбор

    @Test
    void autoSelectBooksTheFirstRecommendedRoomAndIgnoresRoomId() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubRecommend("[room(7, 0), room(3, 1), room(9, 4)]");
        stubConfirm(7, hotelOk());

        postBooking(bearer(alice), 999L, true, "req-auto")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(7))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        HOTEL.verify(1, getRequestedFor(urlPathEqualTo("/api/rooms/recommend"))
                .withQueryParam("startDate", equalTo(start().toString()))
                .withQueryParam("endDate", equalTo(end().toString())));
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(confirmPath(7))));
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(confirmPath(999))));
    }

    @Test
    void autoSelectFallsBackToTheNextRoomWhenTheFirstIsTaken() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubRecommend("[room(7, 0), room(3, 1)]");
        stubConfirm(7, hotelConflict());
        stubConfirm(3, hotelOk());

        var result = postBooking(bearer(alice), null, true, "req-auto-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(3))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        long bookingId = readTree(result).get("id").asLong();

        // у каждой попытки свой ключ идемпотентности; номер 7 не блокировался, поэтому release не вызывается
        JsonNode first = objectMapper.readTree(HOTEL.findAll(postRequestedFor(urlPathEqualTo(confirmPath(7)))).get(0).getBodyAsString());
        JsonNode second = objectMapper.readTree(HOTEL.findAll(postRequestedFor(urlPathEqualTo(confirmPath(3)))).get(0).getBodyAsString());
        assertThat(first.get("requestId").asText()).isEqualTo(bookingId + ":7");
        assertThat(second.get("requestId").asText()).isEqualTo(bookingId + ":3");
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(releasePath(7))));
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void autoSelectFailsWithConflictWhenNoRoomIsLeft() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubRecommend("[]");

        postBooking(bearer(alice), null, true, "req-auto-3").andExpect(status().isConflict());

        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void autoSelectFailsWithServiceUnavailableWhenHotelServiceIsDown() throws Exception {
        User alice = createUser("alice", Role.USER);
        HOTEL.stubFor(WireMock.get(urlPathEqualTo("/api/rooms/recommend")).willReturn(serverError()));

        postBooking(bearer(alice), null, true, "req-auto-4").andExpect(status().isServiceUnavailable());

        HOTEL.verify(3, getRequestedFor(urlPathEqualTo("/api/rooms/recommend")));
        assertThat(bookingRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ отмена

    @Test
    void cancellingAConfirmedBookingReleasesTheRoomAndIsIdempotent(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, hotelOk());
        stubRelease(5, hotelOk());
        var created = postBooking(bearer(alice), 5L, false, "req-cancel").andExpect(status().isCreated());
        long id = readTree(created).get("id").asLong();

        mockMvc.perform(delete("/booking/{id}", id).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(delete("/booking/{id}", id).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(releasePath(5))));
        assertThat(output.getAll()).contains("bookingId=" + id + " статус CONFIRMED -> CANCELLED");
    }

    @Test
    void bookingStaysConfirmedWhenTheRoomCannotBeReleased() throws Exception {
        User alice = createUser("alice", Role.USER);
        stubConfirm(5, hotelOk());
        stubRelease(5, serverError());
        var created = postBooking(bearer(alice), 5L, false, "req-cancel-fail").andExpect(status().isCreated());
        long id = readTree(created).get("id").asLong();

        mockMvc.perform(delete("/booking/{id}", id).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        assertThat(bookingRepository.findById(id)).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------ восстановление

    @Test
    void bookingAbandonedInPendingIsCompensatedByTheBackgroundJob(CapturedOutput output) throws Exception {
        User alice = createUser("alice", Role.USER);
        Booking stale = newBooking(alice, 5, BookingStatus.PENDING, Instant.now().minus(1, ChronoUnit.HOURS), "stale");
        Booking fresh = newBooking(alice, 6, BookingStatus.PENDING, Instant.now(), "fresh");
        stubRelease(5, hotelOk());

        reaper.recoverOnce();

        assertThat(bookingRepository.findById(stale.getId())).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(fresh.getId())).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.PENDING);
        HOTEL.verify(1, postRequestedFor(urlPathEqualTo(releasePath(5))));
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(releasePath(6))));
        assertThat(output.getAll()).contains("bookingId=" + stale.getId() + " застряло в PENDING");
    }

    // ------------------------------------------------------------------ валидация

    @Test
    void invalidBookingRequestsAreRejectedBeforeAnyStateIsCreated() throws Exception {
        User alice = createUser("alice", Role.USER);

        // дата выезда раньше даты заезда
        postBooking(bearer(alice), new ru.mephi.hotelbooking.booking.dto.CreateBookingRequest(5L, end(), start(), false, null))
                .andExpect(status().isBadRequest());
        // дата заезда в прошлом
        postBooking(bearer(alice), new ru.mephi.hotelbooking.booking.dto.CreateBookingRequest(
                5L, java.time.LocalDate.now().minusDays(1), end(), false, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists());
        // без autoSelect номер обязателен
        postBooking(bearer(alice), null, false, null).andExpect(status().isBadRequest());

        assertThat(bookingRepository.count()).isZero();
        HOTEL.verify(0, postRequestedFor(urlPathEqualTo(confirmPath(5))));
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ история и доступ

    @Test
    void historyContainsOnlyOwnBookingsNewestFirst() throws Exception {
        User alice = createUser("alice", Role.USER);
        User bob = createUser("bob", Role.USER);
        Booking older = newBooking(alice, 1, BookingStatus.CONFIRMED, Instant.now().minus(2, ChronoUnit.DAYS), "h-1");
        Booking newer = newBooking(alice, 2, BookingStatus.CANCELLED, Instant.now().minus(1, ChronoUnit.DAYS), "h-2");
        newBooking(bob, 3, BookingStatus.CONFIRMED, Instant.now(), "h-3");

        mockMvc.perform(get("/bookings").header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()));
    }

    @Test
    void usersCannotSeeOrCancelForeignBookingsButAdminsCan() throws Exception {
        User alice = createUser("alice", Role.USER);
        User bob = createUser("bob", Role.USER);
        User admin = createUser("root", Role.ADMIN);
        Booking booking = newBooking(alice, 5, BookingStatus.CONFIRMED, Instant.now(), "own");
        stubRelease(5, hotelOk());

        mockMvc.perform(get("/booking/{id}", booking.getId()).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()));
        mockMvc.perform(get("/booking/{id}", booking.getId()).header(AUTHORIZATION, bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/booking/{id}", booking.getId()).header(AUTHORIZATION, bearer(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/booking/{id}", booking.getId()).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/booking/{id}", 99999).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isNotFound());

        assertThat(bookingRepository.findById(booking.getId())).get()
                .extracting(Booking::getStatus).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------ помощники

    private List<String> sentRequestIds(String path) throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        for (LoggedRequest request : HOTEL.findAll(postRequestedFor(urlPathEqualTo(path)))) {
            ids.add(objectMapper.readTree(request.getBodyAsString()).get("requestId").asText());
        }
        return ids;
    }

    /** Заглушка GET /api/rooms/recommend: JSON-массив строится из вызовов room(id, timesBooked). */
    private static void stubRecommend(String roomsExpression) {
        HOTEL.stubFor(WireMock.get(urlPathEqualTo("/api/rooms/recommend")).willReturn(okJson(expand(roomsExpression))));
    }

    private static String expand(String expression) {
        // "[room(7, 0), room(3, 1)]" -> JSON-массив номеров
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("room\\((\\d+),\\s*(\\d+)\\)").matcher(expression);
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (matcher.find()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":").append(matcher.group(1))
                    .append(",\"hotelId\":1,\"number\":\"").append(matcher.group(1))
                    .append("\",\"available\":true,\"timesBooked\":").append(matcher.group(2)).append('}');
        }
        return json.append(']').toString();
    }
}

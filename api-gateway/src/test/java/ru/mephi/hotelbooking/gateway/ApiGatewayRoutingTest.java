package ru.mephi.hotelbooking.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Шлюз перед заглушками сервисов: маршрутизация и переписывание путей, проверка JWT на входе, публичные эндпойнты,
 * заблокированные внутренние эндпойнты и корреляционный идентификатор.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ApiGatewayRoutingTest {

    /** Изображает и Booking Service, и Hotel Service: их пути не пересекаются. */
    static final WireMockServer DOWNSTREAM = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        DOWNSTREAM.start();
    }

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.booking-uri", () -> "http://localhost:" + DOWNSTREAM.port());
        registry.add("gateway.routes.hotel-uri", () -> "http://localhost:" + DOWNSTREAM.port());
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void resetDownstream() {
        DOWNSTREAM.resetAll();
    }

    // ------------------------------------------------------------------ маршруты бронирований

    @Test
    void bookingRoutesStripTheApiPrefixAndForwardTheJwt() {
        DOWNSTREAM.stubFor(WireMock.get(urlPathEqualTo("/bookings")).willReturn(okJson("[]")));
        DOWNSTREAM.stubFor(WireMock.post(urlPathEqualTo("/booking")).willReturn(
                aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));
        String token = TestTokens.user();

        client.get().uri("/api/bookings/bookings").header(AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
        client.post().uri("/api/bookings/booking").header(AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"roomId\":1}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().json("{\"id\":1}");

        DOWNSTREAM.verify(1, getRequestedFor(urlPathEqualTo("/bookings")).withHeader(AUTHORIZATION, equalTo(token)));
        DOWNSTREAM.verify(1, postRequestedFor(urlPathEqualTo("/booking"))
                .withHeader(AUTHORIZATION, equalTo(token))
                .withRequestBody(equalTo("{\"roomId\":1}")));
    }

    @Test
    void queryStringAndNestedPathsSurviveTheRewrite() {
        DOWNSTREAM.stubFor(WireMock.get(urlPathEqualTo("/booking/42")).willReturn(okJson("{\"id\":42}")));
        DOWNSTREAM.stubFor(WireMock.delete(urlEqualTo("/user?id=7")).willReturn(aResponse().withStatus(204)));

        client.get().uri("/api/bookings/booking/42").header(AUTHORIZATION, TestTokens.user())
                .exchange().expectStatus().isOk();
        client.delete().uri("/api/bookings/user?id=7").header(AUTHORIZATION, TestTokens.admin())
                .exchange().expectStatus().isNoContent();

        DOWNSTREAM.verify(1, getRequestedFor(urlPathEqualTo("/booking/42")));
        DOWNSTREAM.verify(1, com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor(urlEqualTo("/user?id=7")));
    }

    @Test
    void registrationAndLoginArePublic() {
        DOWNSTREAM.stubFor(WireMock.post(urlPathEqualTo("/user/register")).willReturn(
                aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"accessToken\":\"t\"}")));
        DOWNSTREAM.stubFor(WireMock.post(urlPathEqualTo("/user/auth")).willReturn(okJson("{\"accessToken\":\"t\"}")));

        client.post().uri("/api/bookings/user/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange().expectStatus().isCreated();
        client.post().uri("/api/bookings/user/auth").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange().expectStatus().isOk();

        DOWNSTREAM.verify(1, postRequestedFor(urlPathEqualTo("/user/register")));
        DOWNSTREAM.verify(1, postRequestedFor(urlPathEqualTo("/user/auth")));
    }

    @Test
    void otherBookingEndpointsRequireAToken() {
        client.get().uri("/api/bookings/bookings").exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Требуется аутентификация")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("Требуется JWT-токен: передайте его в заголовке Authorization: Bearer <токен>");
        client.post().uri("/api/bookings/booking").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/bookings/user").exchange().expectStatus().isUnauthorized();
        // публичны только POST-запросы на register и auth
        client.get().uri("/api/bookings/user/auth").exchange().expectStatus().isUnauthorized();

        assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
    }

    // ------------------------------------------------------------------ маршруты отелей и номеров

    @Test
    void hotelAndRoomRoutesAreForwardedUnchanged() {
        DOWNSTREAM.stubFor(WireMock.get(urlPathEqualTo("/api/hotels")).willReturn(okJson("[]")));
        DOWNSTREAM.stubFor(WireMock.get(urlPathEqualTo("/api/rooms/recommend")).willReturn(okJson("[]")));
        DOWNSTREAM.stubFor(WireMock.post(urlPathEqualTo("/api/hotels")).willReturn(
                aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));
        String token = TestTokens.user();

        client.get().uri("/api/hotels").header(AUTHORIZATION, token).exchange().expectStatus().isOk();
        client.get().uri("/api/rooms/recommend?startDate=2030-01-01&endDate=2030-01-05").header(AUTHORIZATION, token)
                .exchange().expectStatus().isOk();
        client.post().uri("/api/hotels").header(AUTHORIZATION, TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"name\":\"H\",\"address\":\"A\"}")
                .exchange().expectStatus().isCreated();

        DOWNSTREAM.verify(1, getRequestedFor(urlPathEqualTo("/api/hotels")).withHeader(AUTHORIZATION, equalTo(token)));
        DOWNSTREAM.verify(1, getRequestedFor(urlPathEqualTo("/api/rooms/recommend"))
                .withQueryParam("startDate", equalTo("2030-01-01")));
        DOWNSTREAM.verify(1, postRequestedFor(urlPathEqualTo("/api/hotels")));
    }

    @Test
    void hotelRoutesRequireAToken() {
        client.get().uri("/api/hotels").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/rooms").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/rooms").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();

        assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
    }

    @Test
    void internalEndpointsAreNotPublishedEvenForValidTokens() {
        DOWNSTREAM.stubFor(WireMock.post(WireMock.urlPathMatching("/api/rooms/.*")).willReturn(okJson("{}")));

        for (String token : List.of(TestTokens.user(), TestTokens.admin(), TestTokens.internal())) {
            client.post().uri("/api/rooms/1/confirm-availability").header(AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"requestId\":\"1:1\",\"startDate\":\"2030-01-01\",\"endDate\":\"2030-01-05\"}")
                    .exchange().expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Не найдено")
                    .jsonPath("$.detail").isEqualTo("Маршрут для этого пути не найден");
            client.post().uri("/api/rooms/1/release").header(AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"requestId\":\"1:1\"}")
                    .exchange().expectStatus().isNotFound();
        }
        client.post().uri("/api/rooms/1/release/").header(AUTHORIZATION, TestTokens.internal())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isNotFound();

        assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
    }

    // ------------------------------------------------------------------ проверка JWT на входе

    @Test
    void invalidTokensAreRejectedAtTheEdge() {
        String expired = "Bearer " + TestTokens.token(TestTokens.SECRET, "alice", "USER", Instant.now().minusSeconds(600));
        String foreign = "Bearer " + TestTokens.token("another-secret-another-secret-another-secret-xx", "alice", "USER",
                Instant.now().plusSeconds(600));

        for (String token : List.of(expired, foreign, "Bearer not-a-jwt")) {
            client.get().uri("/api/bookings/bookings").header(AUTHORIZATION, token)
                    .exchange().expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.detail").isEqualTo("Токен недействителен или просрочен");
            client.get().uri("/api/hotels").header(AUTHORIZATION, token)
                    .exchange().expectStatus().isUnauthorized();
        }

        assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
    }

    // ------------------------------------------------------------------ ошибки самого шлюза

    @Test
    void gatewayErrorsAreInRussian() {
        // неизвестный путь: стандартный JSON Spring Boot, но поле error по-русски
        client.get().uri("/api/unknown").accept(MediaType.APPLICATION_JSON).header(AUTHORIZATION, TestTokens.user())
                .exchange().expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Не найдено");
    }

    // ------------------------------------------------------------------ корреляция

    @Test
    void correlationIdIsGeneratedForwardedAndReturned() {
        DOWNSTREAM.stubFor(WireMock.get(urlPathEqualTo("/api/hotels")).willReturn(okJson("[]")));

        var generated = client.get().uri("/api/hotels").header(AUTHORIZATION, TestTokens.user())
                .exchange().expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-Id");
        client.get().uri("/api/hotels").header(AUTHORIZATION, TestTokens.user()).header("X-Correlation-Id", "trace-123")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-123");

        List<LoggedRequest> requests = DOWNSTREAM.findAll(getRequestedFor(urlPathEqualTo("/api/hotels")));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getHeader("X-Correlation-Id")).isEqualTo(generated);
        assertThat(requests.get(1).getHeader("X-Correlation-Id")).isEqualTo("trace-123");
    }
}

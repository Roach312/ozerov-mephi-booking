package ru.mephi.hotelbooking.booking;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.booking.dto.CreateBookingRequest;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.Role;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.booking.security.JwtService;

/**
 * Основа интеграционных тестов. Сервис отелей заменён на WireMock; он находится через (простой) discovery-клиент
 * точно так же, как через Eureka в продакшене, поэтому балансируемый RestClient, тайм-ауты и политика повторов
 * проверяются по-настоящему.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractBookingTest {

    static final WireMockServer HOTEL = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        HOTEL.start();
    }

    @DynamicPropertySource
    static void hotelServiceLocation(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri",
                () -> "http://localhost:" + HOTEL.port());
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected BookingRepository bookingRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    @BeforeEach
    void resetState() {
        HOTEL.resetAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- пользователи и токены

    protected User createUser(String username, Role role) {
        return userRepository.save(new User(username, passwordEncoder.encode("secret123"), role));
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.generateUserToken(user);
    }

    // ---------------------------------------------------------------- запросы

    protected static LocalDate start() {
        return LocalDate.now().plusDays(10);
    }

    protected static LocalDate end() {
        return LocalDate.now().plusDays(15);
    }

    protected ResultActions postBooking(String bearer, Long roomId, boolean autoSelect, String requestId) throws Exception {
        return postBooking(bearer, new CreateBookingRequest(roomId, start(), end(), autoSelect, requestId));
    }

    protected ResultActions postBooking(String bearer, CreateBookingRequest request) throws Exception {
        return mockMvc.perform(post("/booking")
                .header(AUTHORIZATION, bearer)
                .contentType(APPLICATION_JSON)
                .content(json(request)));
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode readTree(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- заглушки сервиса отелей

    protected static String confirmPath(long roomId) {
        return "/api/rooms/" + roomId + "/confirm-availability";
    }

    protected static String releasePath(long roomId) {
        return "/api/rooms/" + roomId + "/release";
    }

    protected static void stubConfirm(long roomId, ResponseDefinitionBuilder response) {
        HOTEL.stubFor(WireMock.post(urlPathEqualTo(confirmPath(roomId))).willReturn(response));
    }

    protected static void stubRelease(long roomId, ResponseDefinitionBuilder response) {
        HOTEL.stubFor(WireMock.post(urlPathEqualTo(releasePath(roomId))).willReturn(response));
    }

    protected static ResponseDefinitionBuilder hotelOk() {
        return okJson("{\"status\":\"HELD\"}");
    }

    protected static ResponseDefinitionBuilder hotelConflict() {
        return aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":409,\"detail\":\"Номер уже зарезервирован на запрошенные даты\"}");
    }

    // ---------------------------------------------------------------- помощники для работы с БД

    protected Booking newBooking(User user, long roomId, BookingStatus status, Instant createdAt, String requestId) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(roomId);
        booking.setStartDate(start());
        booking.setEndDate(end());
        booking.setStatus(status);
        booking.setRequestId(requestId);
        booking.setCreatedAt(createdAt);
        return bookingRepository.save(booking);
    }
}

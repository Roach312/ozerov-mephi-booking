package ru.mephi.hotelbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ru.mephi.hotelbooking.booking.dto.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.Role;
import ru.mephi.hotelbooking.booking.entity.User;

class UserAuthAndAdminTest extends AbstractBookingTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    // ------------------------------------------------------------------ регистрация и вход

    @Test
    void registrationCreatesAUserWithARoleUserTokenValidForOneHour() throws Exception {
        var result = mockMvc.perform(post("/user/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("alice", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        JsonNode body = readTree(result);

        Jwt jwt = jwtDecoder.decode(body.get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(59)));

        // пароль хранится в виде BCrypt-хеша, а не открытым текстом
        User stored = userRepository.findByUsername("alice").orElseThrow();
        assertThat(stored.getPassword()).startsWith("$2").isNotEqualTo("secret123");
    }

    @Test
    void registrationNeverGrantsAdminEvenIfTheClientAsksForIt() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"secret123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findByUsername("mallory")).get()
                .extracting(User::getRole).isEqualTo(Role.USER);
    }

    @Test
    void duplicateUsernameAndInvalidPayloadAreRejected() throws Exception {
        createUser("alice", Role.USER);

        mockMvc.perform(post("/user/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("alice", "secret123"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/user/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("bo", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос"))
                .andExpect(jsonPath("$.detail").value("Ошибка валидации запроса"))
                // сообщения Bean Validation приходят на русском языке
                .andExpect(jsonPath("$.errors.username").value(org.hamcrest.Matchers.containsString("размер")))
                .andExpect(jsonPath("$.errors.password").value(org.hamcrest.Matchers.containsString("размер")));
    }

    @Test
    void loginReturnsATokenThatWorksOnProtectedEndpoints() throws Exception {
        createUser("alice", Role.USER);

        var result = mockMvc.perform(post("/user/auth")
                        .contentType(APPLICATION_JSON)
                        .content(json(new AuthRequest("alice", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));
        String token = readTree(result).get("accessToken").asText();

        mockMvc.perform(get("/bookings").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void wrongCredentialsAreRejectedWithoutRevealingWhatIsWrong() throws Exception {
        createUser("alice", Role.USER);

        mockMvc.perform(post("/user/auth")
                        .contentType(APPLICATION_JSON)
                        .content(json(new AuthRequest("alice", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверное имя пользователя или пароль"));
        mockMvc.perform(post("/user/auth")
                        .contentType(APPLICATION_JSON)
                        .content(json(new AuthRequest("nobody", "secret123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверное имя пользователя или пароль"));
    }

    // ------------------------------------------------------------------ контроль доступа

    @Test
    void userAdministrationIsForbiddenForRegularUsersAndAnonymous() throws Exception {
        User alice = createUser("alice", Role.USER);
        String body = json(new UserCreateRequest("eve", "secret123", Role.ADMIN));

        mockMvc.perform(post("/user").header(AUTHORIZATION, bearer(alice)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/user").header(AUTHORIZATION, bearer(alice)).contentType(APPLICATION_JSON)
                        .content(json(new UserUpdateRequest(alice.getId(), null, null, Role.ADMIN))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/user").param("id", String.valueOf(alice.getId())).header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user").header(AUTHORIZATION, bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByUsername("eve")).isEmpty();
        assertThat(userRepository.findByUsername("alice")).get().extracting(User::getRole).isEqualTo(Role.USER);
    }

    @Test
    void internalServiceTokensCannotUseBookingEndpoints() throws Exception {
        String serviceToken = "Bearer " + jwtService.generateServiceToken();

        mockMvc.perform(get("/bookings").header(AUTHORIZATION, serviceToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/user").header(AUTHORIZATION, serviceToken)).andExpect(status().isForbidden());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        User alice = createUser("alice", Role.USER);
        String token = jwtService.generateUserToken(alice);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/bookings").header(AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ администрирование

    @Test
    void adminManagesUsers() throws Exception {
        User admin = createUser("root", Role.ADMIN);

        // создание
        var created = mockMvc.perform(post("/user")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UserCreateRequest("manager", "secret123", Role.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
        long id = readTree(created).get("id").asLong();

        // чтение
        mockMvc.perform(get("/user/{id}", id).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager"));
        mockMvc.perform(get("/user").header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(content().string(not(containsString("secret123"))));

        // частичное изменение: меняются только переданные поля
        mockMvc.perform(patch("/user")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UserUpdateRequest(id, null, "new-password", Role.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(post("/user/auth")
                        .contentType(APPLICATION_JSON)
                        .content(json(new AuthRequest("manager", "new-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(post("/user/auth")
                        .contentType(APPLICATION_JSON)
                        .content(json(new AuthRequest("manager", "secret123"))))
                .andExpect(status().isUnauthorized());

        // удаление
        mockMvc.perform(delete("/user").param("id", String.valueOf(id)).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/user/{id}", id).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotCreateOrRenameToAnExistingUsername() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User alice = createUser("alice", Role.USER);

        mockMvc.perform(post("/user")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UserCreateRequest("alice", "secret123", Role.USER))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/user")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UserUpdateRequest(alice.getId(), "root", null, null))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/user")
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(json(new UserUpdateRequest(424242L, "ghost", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void userWithActiveBookingsCannotBeDeletedButOneWithoutCanBe() throws Exception {
        User admin = createUser("root", Role.ADMIN);
        User busy = createUser("busy", Role.USER);
        User idle = createUser("idle", Role.USER);
        newBooking(busy, 5, BookingStatus.CONFIRMED, Instant.now(), "busy-1");
        newBooking(idle, 6, BookingStatus.CANCELLED, Instant.now(), "idle-1");

        mockMvc.perform(delete("/user").param("id", String.valueOf(busy.getId())).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/user").param("id", String.valueOf(idle.getId())).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(busy.getId())).isPresent();
        assertThat(userRepository.findById(idle.getId())).isEmpty();
        assertThat(bookingRepository.findAll()).extracting(b -> b.getRequestId()).containsExactly("busy-1");
    }
}

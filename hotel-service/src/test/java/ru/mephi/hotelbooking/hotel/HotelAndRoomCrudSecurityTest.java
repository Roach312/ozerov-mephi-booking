package ru.mephi.hotelbooking.hotel;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;

class HotelAndRoomCrudSecurityTest extends AbstractHotelTest {

    @Test
    void adminCreatesHotelAndUserCanListIt() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new HotelRequest("Plaza", "Main st. 1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Plaza"))
                .andExpect(jsonPath("$.address").value("Main st. 1"));

        mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Plaza"));
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(AUTHORIZATION, TestTokens.user())
                        .contentType(APPLICATION_JSON)
                        .content(json(new HotelRequest("Plaza", "Main st. 1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenAndTokenSignedWithAnotherSecretAreRejected() throws Exception {
        String expired = "Bearer " + TestTokens.token(TestTokens.SECRET, "alice", "USER", Instant.now().minusSeconds(600));
        String foreign = "Bearer " + TestTokens.token("another-secret-another-secret-another-secret-xx", "alice", "USER",
                Instant.now().plusSeconds(600));

        mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, expired)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidHotelPayloadIsRejectedWithValidationErrors() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\",\"address\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос"))
                .andExpect(jsonPath("$.detail").value("Ошибка валидации запроса"))
                // сообщения Bean Validation приходят на русском языке
                .andExpect(jsonPath("$.errors.name").value("не должно быть пустым"))
                .andExpect(jsonPath("$.errors.address", notNullValue()));
    }

    @Test
    void errorMessagesAreInRussian() throws Exception {
        // 404: собственное сообщение сервиса
        mockMvc.perform(get("/api/hotels/9999").header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"))
                .andExpect(jsonPath("$.detail").value("Отель 9999 не найден"));

        // 400: сообщение Spring MVC из messages.properties
        mockMvc.perform(post("/api/hotels")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content("{не json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос"))
                .andExpect(jsonPath("$.detail").value("Не удалось прочитать тело запроса: проверьте формат JSON и значения полей."));

        // 405
        mockMvc.perform(patch("/api/hotels").header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Метод не поддерживается"))
                .andExpect(jsonPath("$.detail").value("Метод 'PATCH' не поддерживается для этого пути."));

        // 404 для неизвестного пути
        mockMvc.perform(get("/api/unknown").header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"));

        // 401 и 403 тоже в формате ProblemDetail на русском языке
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Требуется аутентификация"))
                .andExpect(jsonPath("$.detail").value("Требуется JWT-токен: передайте его в заголовке Authorization: Bearer <токен>"));
        mockMvc.perform(get("/api/hotels").header(AUTHORIZATION, "Bearer мусор"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Токен недействителен или просрочен"));
        mockMvc.perform(post("/api/hotels")
                        .header(AUTHORIZATION, TestTokens.user())
                        .contentType(APPLICATION_JSON)
                        .content(json(new HotelRequest("Plaza", "Main st. 1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Доступ запрещён"))
                .andExpect(jsonPath("$.detail").value("Недостаточно прав для выполнения этой операции"));
    }

    @Test
    void adminUpdatesAndDeletesHotel() throws Exception {
        Hotel hotel = newHotel("Old name");

        mockMvc.perform(put("/api/hotels/{id}", hotel.getId())
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new HotelRequest("New name", "New address"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"));

        mockMvc.perform(get("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("New address"));

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void hotelWithRoomsCannotBeDeleted() throws Exception {
        Hotel hotel = newHotel("Busy");
        newRoom(hotel, "1", 0, true);

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void adminManagesRooms() throws Exception {
        Hotel hotel = newHotel("Plaza");

        mockMvc.perform(post("/api/rooms")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "101", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.number").value("101"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));

        Room room = roomRepository.findAll().get(0);

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "102", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("102"))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/rooms/{id}", room.getId()).header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("102"));

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rooms/{id}", room.getId()).header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateRoomNumberInHotelIsRejected() throws Exception {
        Hotel hotel = newHotel("Plaza");
        newRoom(hotel, "101", 0, true);

        mockMvc.perform(post("/api/rooms")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "101", true))))
                .andExpect(status().isConflict());
    }

    @Test
    void roomForUnknownHotelIsRejected() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header(AUTHORIZATION, TestTokens.admin())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoomRequest(9999L, "101", true))))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotModifyRooms() throws Exception {
        Hotel hotel = newHotel("Plaza");
        Room room = newRoom(hotel, "101", 0, true);

        mockMvc.perform(post("/api/rooms")
                        .header(AUTHORIZATION, TestTokens.user())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "102", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isForbidden());
    }
}

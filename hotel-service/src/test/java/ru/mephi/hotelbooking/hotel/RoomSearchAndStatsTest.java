package ru.mephi.hotelbooking.hotel;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

class RoomSearchAndStatsTest extends AbstractHotelTest {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = LocalDate.of(2030, 5, 15);

    @Test
    void freeRoomsExcludeOperationallyUnavailableOnesAndAreNotSpeciallySorted() throws Exception {
        Hotel hotel = newHotel("Plaza");
        Room r1 = newRoom(hotel, "1", 5, true);
        Room r2 = newRoom(hotel, "2", 0, true);
        newRoom(hotel, "3", 0, false);

        mockMvc.perform(get("/api/rooms").header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(r1.getId().intValue(), r2.getId().intValue())));
    }

    @Test
    void recommendedRoomsAreOrderedByTimesBookedThenById() throws Exception {
        Hotel hotel = newHotel("Plaza");
        Room busy = newRoom(hotel, "1", 2, true);
        Room freshA = newRoom(hotel, "2", 0, true);
        Room freshB = newRoom(hotel, "3", 0, true);
        Room medium = newRoom(hotel, "4", 1, true);
        newRoom(hotel, "5", 0, false);

        mockMvc.perform(get("/api/rooms/recommend").header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        freshA.getId().intValue(),
                        freshB.getId().intValue(),
                        medium.getId().intValue(),
                        busy.getId().intValue())));
    }

    @Test
    void recommendationIsAlsoAvailableForInternalCallers() throws Exception {
        Hotel hotel = newHotel("Plaza");
        newRoom(hotel, "1", 0, true);

        mockMvc.perform(get("/api/rooms/recommend")
                        .param("startDate", START.toString())
                        .param("endDate", END.toString())
                        .header(AUTHORIZATION, TestTokens.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void roomsBlockedForOverlappingPeriodAreExcludedWhenDatesAreGiven() throws Exception {
        Hotel hotel = newHotel("Plaza");
        Room blocked = newRoom(hotel, "1", 1, true);
        Room releasedOnly = newRoom(hotel, "2", 0, true);
        Room adjacent = newRoom(hotel, "3", 0, true);
        Room free = newRoom(hotel, "4", 3, true);
        slot(blocked, "r-1", START.plusDays(1), END.plusDays(1), SlotStatus.HELD);
        slot(releasedOnly, "r-2", START, END, SlotStatus.RELEASED);
        slot(adjacent, "r-3", END, END.plusDays(3), SlotStatus.HELD);

        mockMvc.perform(get("/api/rooms/recommend")
                        .param("startDate", START.toString())
                        .param("endDate", END.toString())
                        .header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        releasedOnly.getId().intValue(),
                        adjacent.getId().intValue(),
                        free.getId().intValue())));

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", START.toString())
                        .param("endDate", END.toString())
                        .header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void incompleteOrInvalidPeriodIsRejected() throws Exception {
        mockMvc.perform(get("/api/rooms/recommend")
                        .param("startDate", START.toString())
                        .header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", END.toString())
                        .param("endDate", START.toString())
                        .header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminSeesAggregatedLoadStatistics() throws Exception {
        Hotel loaded = newHotel("Loaded");
        Room hot = newRoom(loaded, "1", 3, true);
        Room cold = newRoom(loaded, "2", 1, true);
        Hotel empty = newHotel("Empty");

        mockMvc.perform(get("/api/rooms/stats").header(AUTHORIZATION, TestTokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].hotelId").value(loaded.getId()))
                .andExpect(jsonPath("$[0].roomCount").value(2))
                .andExpect(jsonPath("$[0].totalTimesBooked").value(4))
                .andExpect(jsonPath("$[0].averageTimesBooked", closeTo(2.0, 0.0001)))
                .andExpect(jsonPath("$[0].rooms[*].id", contains(hot.getId().intValue(), cold.getId().intValue())))
                .andExpect(jsonPath("$[1].hotelId").value(empty.getId()))
                .andExpect(jsonPath("$[1].roomCount").value(0))
                .andExpect(jsonPath("$[1].totalTimesBooked").value(0))
                .andExpect(jsonPath("$[1].rooms", hasSize(0)));
    }

    @Test
    void statisticsAreForAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/rooms/stats").header(AUTHORIZATION, TestTokens.user()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rooms/stats").header(AUTHORIZATION, TestTokens.internal()))
                .andExpect(status().isForbidden());
    }

    private void slot(Room room, String requestId, LocalDate start, LocalDate end, SlotStatus status) {
        RoomSlot slot = new RoomSlot();
        slot.setRoom(room);
        slot.setRequestId(requestId);
        slot.setStartDate(start);
        slot.setEndDate(end);
        slot.setStatus(status);
        slotRepository.save(slot);
    }
}

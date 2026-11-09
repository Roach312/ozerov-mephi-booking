package ru.mephi.hotelbooking.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityRequest;
import ru.mephi.hotelbooking.hotel.dto.ReleaseRequest;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

class RoomAvailabilityTest extends AbstractHotelTest {

    private static final LocalDate START = LocalDate.of(2030, 1, 10);
    private static final LocalDate END = LocalDate.of(2030, 1, 15);

    @Test
    void confirmBlocksTheRoomAndIncrementsTimesBooked() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        confirm(room.getId(), "42:1", 42L, START, END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("42:1"))
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.bookingId").value(42))
                .andExpect(jsonPath("$.status").value("HELD"));

        assertThat(timesBooked(room)).isEqualTo(1);
        assertThat(slotRepository.findByRequestId("42:1")).get()
                .extracting(RoomSlot::getStatus).isEqualTo(SlotStatus.HELD);
    }

    @Test
    void repeatedDeliveryOfTheSameRequestDoesNotChangeStateAgain() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        confirm(room.getId(), "42:1", 42L, START, END).andExpect(status().isOk());
        confirm(room.getId(), "42:1", 42L, START, END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));
        confirm(room.getId(), "42:1", 42L, START, END).andExpect(status().isOk());

        assertThat(slotRepository.count()).isEqualTo(1);
        assertThat(timesBooked(room)).isEqualTo(1);
    }

    @Test
    void overlappingPeriodIsRejectedWithConflict() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);
        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isOk());

        confirm(room.getId(), "2:1", 2L, START.plusDays(2), END.plusDays(2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        confirm(room.getId(), "3:1", 3L, START.minusDays(2), START.plusDays(1))
                .andExpect(status().isConflict());
        confirm(room.getId(), "4:1", 4L, START.plusDays(1), END.minusDays(1))
                .andExpect(status().isConflict());

        assertThat(slotRepository.count()).isEqualTo(1);
        assertThat(timesBooked(room)).isEqualTo(1);
    }

    @Test
    void adjacentPeriodsDoNotConflict() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isOk());
        confirm(room.getId(), "2:1", 2L, END, END.plusDays(3)).andExpect(status().isOk());
        confirm(room.getId(), "3:1", 3L, START.minusDays(3), START).andExpect(status().isOk());

        assertThat(timesBooked(room)).isEqualTo(3);
    }

    @Test
    void operationallyUnavailableRoomIsRejected() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, false);

        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isConflict());

        assertThat(slotRepository.count()).isZero();
    }

    @Test
    void releaseFreesTheSlotAndTheRoomCanBeBlockedAgain() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);
        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isOk());

        release(room.getId(), "1:1", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.changed").value(true));

        assertThat(timesBooked(room)).isZero();
        confirm(room.getId(), "2:1", 2L, START, END).andExpect(status().isOk());
        assertThat(timesBooked(room)).isEqualTo(1);
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);
        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isOk());

        release(room.getId(), "1:1", 1L).andExpect(jsonPath("$.changed").value(true));
        release(room.getId(), "1:1", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
        release(room.getId(), "1:1", 1L).andExpect(status().isOk());

        assertThat(timesBooked(room)).isZero();
    }

    @Test
    void confirmWithTheSameRequestIdAfterReleaseIsRejected() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);
        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isOk());
        release(room.getId(), "1:1", 1L).andExpect(status().isOk());

        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isConflict());

        assertThat(timesBooked(room)).isZero();
    }

    @Test
    void releaseBeforeConfirmRecordsTombstoneSoLateConfirmIsRejected() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        release(room.getId(), "1:1", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.status").value("RELEASED"));
        confirm(room.getId(), "1:1", 1L, START, END).andExpect(status().isConflict());

        assertThat(timesBooked(room)).isZero();
        assertThat(slotRepository.findByRequestId("1:1")).get()
                .extracting(RoomSlot::getStatus).isEqualTo(SlotStatus.RELEASED);
    }

    @Test
    void requestIdCannotBeReusedForAnotherRoomOrPeriod() throws Exception {
        Hotel hotel = newHotel("Plaza");
        Room first = newRoom(hotel, "101", 0, true);
        Room second = newRoom(hotel, "102", 0, true);
        confirm(first.getId(), "1:1", 1L, START, END).andExpect(status().isOk());

        confirm(second.getId(), "1:1", 1L, START, END).andExpect(status().isConflict());
        confirm(first.getId(), "1:1", 1L, START, END.plusDays(1)).andExpect(status().isConflict());

        assertThat(timesBooked(second)).isZero();
    }

    @Test
    void unknownRoomReturnsNotFound() throws Exception {
        confirm(9999L, "1:1", 1L, START, END).andExpect(status().isNotFound());
        release(9999L, "1:1", 1L).andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        confirm(room.getId(), "1:1", 1L, END, START).andExpect(status().isBadRequest());
        confirm(room.getId(), "1:1", 1L, START, START).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .header(AUTHORIZATION, TestTokens.internal())
                        .contentType(APPLICATION_JSON)
                        .content("{\"bookingId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyInternalCallersMayUseConsistencyEndpoints() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);
        String body = json(new AvailabilityRequest("1:1", 1L, START, END));

        for (String token : new String[]{TestTokens.user(), TestTokens.admin()}) {
            mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                            .header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/rooms/{id}/release", room.getId())
                            .header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                            .content(json(new ReleaseRequest("1:1", 1L))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", room.getId())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        assertThat(slotRepository.count()).isZero();
    }

    private ResultActions confirm(long roomId, String requestId, long bookingId, LocalDate start, LocalDate end)
            throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                .header(AUTHORIZATION, TestTokens.internal())
                .contentType(APPLICATION_JSON)
                .content(json(new AvailabilityRequest(requestId, bookingId, start, end))));
    }

    private ResultActions release(long roomId, String requestId, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", roomId)
                .header(AUTHORIZATION, TestTokens.internal())
                .contentType(APPLICATION_JSON)
                .content(json(new ReleaseRequest(requestId, bookingId))));
    }

    private int timesBooked(Room room) {
        return roomRepository.findById(room.getId()).orElseThrow().getTimesBooked();
    }
}

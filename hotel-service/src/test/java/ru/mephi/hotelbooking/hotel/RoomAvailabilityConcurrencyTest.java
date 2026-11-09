package ru.mephi.hotelbooking.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityRequest;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

/** Гонки за один номер: пессимистичная блокировка строки должна выстроить их по очереди без дублей. */
class RoomAvailabilityConcurrencyTest extends AbstractHotelTest {

    private static final LocalDate START = LocalDate.of(2030, 2, 1);
    private static final LocalDate END = LocalDate.of(2030, 2, 6);
    private static final int THREADS = 8;

    @Test
    void concurrentAttemptsToBlockTheSameRoomForOverlappingDatesHaveExactlyOneWinner() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        // у каждого потока свой requestId и слегка сдвинутые пересекающиеся даты
        List<Integer> statuses = runConcurrently(n ->
                confirm(room.getId(), "req-" + n, 100L + n, START.plusDays(n % 3), END.plusDays(n % 3)));

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(THREADS - 1);

        List<RoomSlot> held = slotRepository.findAll().stream()
                .filter(slot -> slot.getStatus() == SlotStatus.HELD).toList();
        assertThat(held).hasSize(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    @Test
    void concurrentRedeliveryOfTheSameRequestCreatesASingleSlot() throws Exception {
        Room room = newRoom(newHotel("Plaza"), "101", 0, true);

        List<Integer> statuses = runConcurrently(n -> confirm(room.getId(), "same-request", 7L, START, END));

        assertThat(statuses).containsOnly(200);
        assertThat(slotRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    private int confirm(Long roomId, String requestId, Long bookingId, LocalDate start, LocalDate end) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                        .header(AUTHORIZATION, TestTokens.internal())
                        .contentType(APPLICATION_JSON)
                        .content(json(new AvailabilityRequest(requestId, bookingId, start, end))))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> runConcurrently(ThrowingIntFunction task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                Callable<Integer> call = () -> {
                    ready.countDown();
                    start.await();
                    return task.apply(n);
                };
                futures.add(pool.submit(call));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingIntFunction {
        int apply(int n) throws Exception;
    }
}

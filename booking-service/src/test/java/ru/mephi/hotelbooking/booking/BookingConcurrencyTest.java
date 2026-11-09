package ru.mephi.hotelbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.mephi.hotelbooking.booking.client.HotelCallRejectedException;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.Role;

/**
 * Гонки между одновременными запросами. Сервис отелей заменён арбитром в памяти с тем же контрактом
 * (первый requestId выигрывает номер, остальные получают 409; тот же requestId идемпотентен), поэтому тест
 * проверяет поведение самого Booking Service: без дублей, ровно один победитель, правильные коды ответов.
 */
class BookingConcurrencyTest extends AbstractBookingTest {

    private static final int THREADS = 8;

    @MockitoBean
    private HotelClient hotelClient;

    private final Map<Long, String> roomOwners = new ConcurrentHashMap<>();

    @BeforeEach
    void installHotelArbiter() {
        roomOwners.clear();
        doAnswer(invocation -> {
            Long roomId = invocation.getArgument(0);
            String requestId = invocation.getArgument(1);
            String owner = roomOwners.putIfAbsent(roomId, requestId);
            if (owner != null && !owner.equals(requestId)) {
                throw new HotelCallRejectedException(409, "номер занят");
            }
            return null;
        }).when(hotelClient).confirmAvailability(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentBookingsOfTheSameRoomForOverlappingDatesHaveExactlyOneWinner() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(bearer(createUser("user" + i, Role.USER)));
        }

        List<Integer> statuses = runConcurrently(n -> postBooking(tokens.get(n), 5L, false, "race-" + n)
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(THREADS - 1);

        List<Booking> all = bookingRepository.findAll();
        assertThat(all).filteredOn(b -> b.getStatus() == BookingStatus.CONFIRMED).hasSize(1);
        assertThat(all).filteredOn(b -> b.getStatus() == BookingStatus.PENDING).isEmpty();
        assertThat(all).filteredOn(b -> b.getStatus() != BookingStatus.CONFIRMED)
                .allMatch(b -> b.getStatus() == BookingStatus.CANCELLED);
    }

    @Test
    void concurrentRedeliveryOfOneRequestCreatesASingleBooking() throws Exception {
        String token = bearer(createUser("alice", Role.USER));

        List<Integer> statuses = runConcurrently(n -> postBooking(token, 5L, false, "same-request")
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(THREADS - 1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingRepository.findAll().get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        // удалённый шаг выполнила только победившая доставка; повторные доставки вернули сохранённый результат
        verify(hotelClient, times(1)).confirmAvailability(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentBookingsOfDifferentRoomsAllSucceed() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(bearer(createUser("user" + i, Role.USER)));
        }

        List<Integer> statuses = runConcurrently(n -> postBooking(tokens.get(n), 100L + n, false, "room-" + n)
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(201);
        assertThat(bookingRepository.findAll()).hasSize(THREADS)
                .allMatch(b -> b.getStatus() == BookingStatus.CONFIRMED);
        verify(hotelClient, times(THREADS)).confirmAvailability(any(), any(), any(), any(), any());
    }

    private List<Integer> runConcurrently(ThrowingIntFunction task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                Callable<Integer> call = () -> {
                    ready.countDown();
                    go.await();
                    return task.apply(n);
                };
                futures.add(pool.submit(call));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
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

package ru.mephi.hotelbooking.booking.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.mephi.hotelbooking.booking.entity.Booking;

/**
 * Фоновое восстановление саги:
 * <ul>
 *     <li>бронирования, брошенные в PENDING (сбой между шагами саги), компенсируются: блокировка снимается в
 *     сервисе отелей, бронирование переходит в CANCELLED;</li>
 *     <li>CANCELLED-бронирования, у которых компенсирующий release не дошёл до сервиса отелей, повторяются.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleBookingReaper {

    private final BookingTxService tx;
    private final BookingSagaService saga;
    private final ReaperProperties properties;

    @Scheduled(fixedDelayString = "${booking.reaper.interval-ms:30000}",
            initialDelayString = "${booking.reaper.interval-ms:30000}")
    public void scheduledRun() {
        if (properties.isEnabled()) {
            recoverOnce();
        }
    }

    /** Один проход восстановления (его же напрямую вызывают тесты). */
    public void recoverOnce() {
        Instant threshold = Instant.now().minus(properties.getStaleAfter());
        for (Booking stale : tx.findStalePending(threshold)) {
            withBookingMdc(stale, () -> {
                log.warn("bookingId={} застряло в PENDING с {}: запускаем компенсацию", stale.getId(), stale.getCreatedAt());
                saga.compensate(stale, "Восстановлено фоновой задачей: бронирование застряло в PENDING");
            });
        }
        for (Booking cancelled : tx.findCancelledWithPendingRelease()) {
            withBookingMdc(cancelled, () -> {
                if (saga.releaseSlot(cancelled)) {
                    tx.setReleasePending(cancelled.getId(), false);
                    log.info("bookingId={} отложенная компенсация завершена: блокировка снята", cancelled.getId());
                }
            });
        }
    }

    private void withBookingMdc(Booking booking, Runnable action) {
        MDC.put("bookingId", String.valueOf(booking.getId()));
        MDC.put("requestId", booking.getRequestId());
        try {
            action.run();
        } finally {
            MDC.remove("bookingId");
            MDC.remove("requestId");
        }
    }
}

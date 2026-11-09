package ru.mephi.hotelbooking.booking.service;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "booking.reaper")
public class ReaperProperties {

    private boolean enabled = true;

    /** Период запуска фоновой задачи (миллисекунды). */
    private long intervalMs = 30_000;

    /**
     * Бронирование, остающееся в PENDING дольше этого срока, считается брошенным (например, сервис упал в середине
     * саги). Срок должен превышать суммарный бюджет тайм-аутов и повторов одной саги.
     */
    private Duration staleAfter = Duration.ofMinutes(2);
}

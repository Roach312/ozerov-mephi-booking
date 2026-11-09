package ru.mephi.hotelbooking.booking.client;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Тайм-ауты и политика повторов для вызовов сервиса отелей. */
@Getter
@Setter
@ConfigurationProperties(prefix = "hotel.client")
public class HotelClientProperties {

    /** Логическое имя сервиса, которое балансировщик нагрузки разрешает через Eureka. */
    private String baseUrl = "http://hotel-service";

    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(2);

    /** Общее число попыток, включая первую. */
    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofMillis(200);
    private double backoffMultiplier = 2.0;
    private Duration maxBackoff = Duration.ofSeconds(2);
}

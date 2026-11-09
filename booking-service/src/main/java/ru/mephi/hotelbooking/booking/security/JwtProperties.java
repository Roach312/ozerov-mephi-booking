package ru.mephi.hotelbooking.booking.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Общий секрет HS256, не короче 32 байт. Одно и то же значение задаётся во всех сервисах. */
    private String secret;

    /** Срок действия пользовательских токенов. */
    private Duration expiration = Duration.ofHours(1);

    /** Срок действия короткоживущих токенов для вызовов между сервисами. */
    private Duration serviceTokenTtl = Duration.ofSeconds(60);

    private String issuer = "booking-service";
}

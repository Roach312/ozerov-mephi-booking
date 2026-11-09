package ru.mephi.hotelbooking.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Администратор, создаваемый при старте (база in-memory, получить администратора иначе нельзя). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    private boolean enabled = true;
    private String username = "admin";
    private String password = "admin12345";
}

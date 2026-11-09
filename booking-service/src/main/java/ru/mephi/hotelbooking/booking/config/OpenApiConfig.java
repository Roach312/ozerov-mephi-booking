package ru.mephi.hotelbooking.booking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API сервиса бронирований",
                version = "1.0",
                description = "Регистрация и вход (JWT на 1 час), бронирования с двухшаговым согласованием с сервисом отелей "
                        + "(PENDING -> CONFIRMED / CANCELLED с компенсацией) и администрирование пользователей."),
        servers = {
                @Server(url = "/api/bookings", description = "Через API Gateway (шлюз отбрасывает префикс /api/bookings)"),
                @Server(url = "/", description = "Напрямую к Booking Service")
        })
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT, полученный через POST /user/auth или /user/register (без слова Bearer)")
public class OpenApiConfig {
}

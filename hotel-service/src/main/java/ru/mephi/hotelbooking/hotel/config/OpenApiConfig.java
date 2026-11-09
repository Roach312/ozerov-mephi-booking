package ru.mephi.hotelbooking.hotel.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "API сервиса управления отелями",
        version = "1.0",
        description = "Отели, номера, рекомендации номеров по загрузке и статистика загруженности. "
                + "Эндпойнты с пометкой INTERNAL использует Booking Service, через шлюз они не публикуются. "
                + "Токен выдаёт Booking Service: POST /api/bookings/user/auth."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT, полученный через POST /user/auth или /user/register (без слова Bearer)")
public class OpenApiConfig {
}

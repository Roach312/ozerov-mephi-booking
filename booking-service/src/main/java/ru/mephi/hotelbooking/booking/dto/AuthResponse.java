package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.mephi.hotelbooking.booking.entity.Role;

public record AuthResponse(
        @Schema(description = "JWT для заголовка 'Authorization: Bearer <токен>'") String accessToken,
        @Schema(description = "Тип токена", example = "Bearer") String tokenType,
        @Schema(description = "Срок действия токена в секундах", example = "3600") long expiresIn,
        Long userId,
        String username,
        Role role) {
}

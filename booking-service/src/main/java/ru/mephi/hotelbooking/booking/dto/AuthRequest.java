package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Данные для входа")
public record AuthRequest(
        @Schema(description = "Имя пользователя", example = "alice") @NotBlank String username,
        @Schema(description = "Пароль", example = "secret123") @NotBlank String password) {
}

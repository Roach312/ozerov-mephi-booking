package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Саморегистрация. Новые пользователи всегда получают роль USER.")
public record RegisterRequest(
        @Schema(description = "Имя пользователя (3-64 символа)", example = "alice") @NotBlank @Size(min = 3, max = 64) String username,
        @Schema(description = "Пароль (6-128 символов)", example = "secret123") @NotBlank @Size(min = 6, max = 128) String password) {
}

package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mephi.hotelbooking.booking.entity.Role;

@Schema(description = "Создание пользователя администратором")
public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 6, max = 128) String password,
        @Schema(description = "Роль пользователя: USER или ADMIN") @NotNull Role role) {
}

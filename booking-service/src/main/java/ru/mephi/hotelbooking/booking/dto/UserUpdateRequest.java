package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mephi.hotelbooking.booking.entity.Role;

@Schema(description = "Частичное изменение пользователя: меняются только переданные поля")
public record UserUpdateRequest(
        @Schema(description = "Идентификатор изменяемого пользователя") @NotNull Long id,
        @Size(min = 3, max = 64) String username,
        @Size(min = 6, max = 128) String password,
        Role role) {
}

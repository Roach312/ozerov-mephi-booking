package ru.mephi.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание или изменение номера")
public record RoomRequest(
        @Schema(description = "Идентификатор отеля", example = "1") @NotNull Long hotelId,
        @Schema(description = "Номер комнаты (уникален в пределах отеля)", example = "101") @NotBlank @Size(max = 32) String number,
        @Schema(description = "Операционная доступность (ремонт, вывод из эксплуатации). По умолчанию true.", example = "true")
        Boolean available) {
}

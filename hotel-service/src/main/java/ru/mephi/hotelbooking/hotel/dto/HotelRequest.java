package ru.mephi.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание или изменение отеля")
public record HotelRequest(
        @Schema(description = "Название отеля", example = "Гранд Отель") @NotBlank @Size(max = 255) String name,
        @Schema(description = "Адрес отеля", example = "Москва, ул. Тверская, 1") @NotBlank @Size(max = 255) String address) {
}

package ru.mephi.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Внутренний запрос: снять временную блокировку, созданную с тем же requestId")
public record ReleaseRequest(
        @Schema(description = "Ключ идемпотентности, с которым создавалась блокировка", example = "42:7")
        @NotBlank @Size(max = 128) String requestId,
        @Schema(description = "Идентификатор бронирования (сквозная корреляция)", example = "42") Long bookingId) {
}

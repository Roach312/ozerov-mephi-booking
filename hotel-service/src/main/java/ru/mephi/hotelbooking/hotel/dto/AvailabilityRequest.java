package ru.mephi.hotelbooking.hotel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Внутренний запрос: временно заблокировать номер на период [startDate, endDate)")
public record AvailabilityRequest(
        @Schema(description = "Корреляционный идентификатор и ключ идемпотентности запроса", example = "42:7")
        @NotBlank @Size(max = 128) String requestId,
        @Schema(description = "Идентификатор бронирования (сквозная корреляция)", example = "42") Long bookingId,
        @Schema(description = "Дата заезда (включительно)") @NotNull LocalDate startDate,
        @Schema(description = "Дата выезда (не включительно)") @NotNull LocalDate endDate) {
}

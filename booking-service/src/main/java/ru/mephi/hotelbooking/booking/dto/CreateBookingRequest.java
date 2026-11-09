package ru.mephi.hotelbooking.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Запрос на бронирование на полуоткрытый период [startDate, endDate)")
public record CreateBookingRequest(
        @Schema(description = "Номер для бронирования. Игнорируется, если autoSelect=true.", example = "3") Long roomId,
        @Schema(description = "Дата заезда (включительно), не в прошлом", example = "2030-01-10")
        @NotNull @FutureOrPresent LocalDate startDate,
        @Schema(description = "Дата выезда (не включительно)", example = "2030-01-15") @NotNull LocalDate endDate,
        @Schema(description = "true: автоматически подобрать наименее загруженный свободный номер (roomId игнорируется)", example = "false")
        boolean autoSelect,
        @Schema(description = "Ключ идемпотентности на стороне клиента. Повторная отправка запроса с тем же ключом "
                + "не создаёт второго бронирования. Если не указан, генерируется автоматически.",
                example = "7f0c2a52-2b0e-4d36-8f61-5a1e3c9d2b11")
        @Size(max = 128) String requestId) {
}

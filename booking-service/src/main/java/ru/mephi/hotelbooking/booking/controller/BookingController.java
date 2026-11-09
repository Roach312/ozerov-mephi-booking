package ru.mephi.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.booking.dto.BookingResponse;
import ru.mephi.hotelbooking.booking.dto.CreateBookingRequest;
import ru.mephi.hotelbooking.booking.security.CurrentUser;
import ru.mephi.hotelbooking.booking.service.BookingQueryService;
import ru.mephi.hotelbooking.booking.service.BookingSagaService;

@RestController
@RequiredArgsConstructor
@Tag(name = "Бронирования", description = "Создание, просмотр и отмена бронирований")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingSagaService sagaService;
    private final BookingQueryService queryService;

    @PostMapping("/booking")
    @Operation(summary = "Создать бронирование (USER)",
            description = "Создаёт бронирование в статусе PENDING, просит сервис отелей подтвердить доступность и переводит "
                    + "его в CONFIRMED; при ошибке или тайм-ауте бронирование становится CANCELLED, а блокировка в сервисе "
                    + "отелей снимается. Ответы: 201 создано; 200 сохранённый результат уже обработанного requestId; "
                    + "409 номер занят (в том числе проигранная гонка); 503 сервис отелей не ответил или превышен "
                    + "тайм-аут (бронирование отменено).")
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateBookingRequest request) {
        BookingSagaService.CreationResult result = sagaService.create(CurrentUser.from(jwt), request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.booking());
    }

    @GetMapping("/bookings")
    @Operation(summary = "История бронирований текущего пользователя (USER)")
    public List<BookingResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return queryService.history(CurrentUser.from(jwt));
    }

    @GetMapping("/booking/{id}")
    @Operation(summary = "Бронирование по идентификатору (USER: только своё)")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queryService.get(CurrentUser.from(jwt), id);
    }

    @DeleteMapping("/booking/{id}")
    @Operation(summary = "Отменить бронирование (USER: только своё)",
            description = "Идемпотентно. Подтверждённое бронирование сначала освобождает номер в сервисе отелей.")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return sagaService.cancel(CurrentUser.from(jwt), id);
    }
}

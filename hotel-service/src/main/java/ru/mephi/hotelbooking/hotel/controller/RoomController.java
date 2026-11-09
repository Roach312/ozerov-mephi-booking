package ru.mephi.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityRequest;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityResponse;
import ru.mephi.hotelbooking.hotel.dto.ReleaseRequest;
import ru.mephi.hotelbooking.hotel.dto.ReleaseResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomLoadResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.service.RoomAvailabilityService;
import ru.mephi.hotelbooking.hotel.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Номера", description = "Управление номерами, поиск свободных и статистика загруженности")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;
    private final RoomAvailabilityService availabilityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить номер в отель (ADMIN)")
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @GetMapping
    @Operation(summary = "Список свободных номеров (USER)",
            description = "Операционно доступные номера без особой сортировки. Если указаны startDate и endDate, "
                    + "номера, заблокированные на этот период, исключаются.")
    public List<RoomResponse> free(
            @Parameter(description = "Начало периода (включительно), дата ISO") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Конец периода (не включительно), дата ISO") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return roomService.findFree(startDate, endDate);
    }

    @GetMapping("/recommend")
    @Operation(summary = "Рекомендованные номера (USER)",
            description = "Те же свободные номера, отсортированные по возрастанию times_booked (при равенстве по id) "
                    + "для равномерной загрузки.")
    public List<RoomResponse> recommend(
            @Parameter(description = "Начало периода (включительно), дата ISO") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Конец периода (не включительно), дата ISO") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return roomService.recommend(startDate, endDate);
    }

    @GetMapping("/stats")
    @Operation(summary = "Статистика загруженности номеров (ADMIN)",
            description = "Агрегированный times_booked по каждому отелю и разбивка по номерам.")
    public List<RoomLoadResponse> stats() {
        return roomService.loadStatistics();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Номер по идентификатору (USER)")
    public RoomResponse get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить номер (ADMIN)")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить номер без активных резервов (ADMIN)")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }

    @PostMapping("/{id}/confirm-availability")
    @Operation(summary = "INTERNAL: подтвердить доступность и временно заблокировать номер",
            description = "Используется Booking Service на шаге согласования. Идемпотентно по requestId. "
                    + "Через API Gateway не публикуется.")
    public AvailabilityResponse confirmAvailability(@PathVariable Long id,
                                                    @Valid @RequestBody AvailabilityRequest request) {
        return availabilityService.confirm(id, request);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "INTERNAL: снять временную блокировку (компенсация)",
            description = "Идемпотентно по requestId. Через API Gateway не публикуется.")
    public ReleaseResponse release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest request) {
        return availabilityService.release(id, request);
    }
}

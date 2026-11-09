package ru.mephi.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;
import ru.mephi.hotelbooking.hotel.service.HotelService;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Tag(name = "Отели", description = "Управление отелями")
@SecurityRequirement(name = "bearerAuth")
public class HotelController {

    private final HotelService hotelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить отель (ADMIN)")
    public HotelResponse create(@Valid @RequestBody HotelRequest request) {
        return hotelService.create(request);
    }

    @GetMapping
    @Operation(summary = "Список отелей (USER)")
    public List<HotelResponse> list() {
        return hotelService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отель по идентификатору (USER)")
    public HotelResponse get(@PathVariable Long id) {
        return hotelService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить отель (ADMIN)")
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest request) {
        return hotelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить отель без номеров (ADMIN)")
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }
}

package ru.mephi.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.booking.dto.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.AuthResponse;
import ru.mephi.hotelbooking.booking.dto.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.UserResponse;
import ru.mephi.hotelbooking.booking.dto.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.service.UserService;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Пользователи", description = "Регистрация, вход и администрирование пользователей")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Зарегистрироваться и получить JWT (публичный)")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/auth")
    @Operation(summary = "Войти и получить JWT (публичный)")
    public AuthResponse authenticate(@Valid @RequestBody AuthRequest request) {
        return userService.authenticate(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Создать пользователя с любой ролью (ADMIN)")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PatchMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Изменить данные пользователя (ADMIN)", description = "Меняются только переданные поля.")
    public UserResponse update(@Valid @RequestBody UserUpdateRequest request) {
        return userService.update(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Удалить пользователя (ADMIN)", description = "Возвращает 409, если у пользователя есть активные бронирования.")
    public void delete(@Parameter(description = "Идентификатор пользователя") @RequestParam Long id) {
        userService.delete(id);
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Список пользователей (ADMIN)")
    public List<UserResponse> list() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Пользователь по идентификатору (ADMIN)")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }
}

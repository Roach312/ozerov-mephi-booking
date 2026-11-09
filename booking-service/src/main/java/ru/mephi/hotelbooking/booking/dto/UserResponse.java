package ru.mephi.hotelbooking.booking.dto;

import ru.mephi.hotelbooking.booking.entity.Role;

public record UserResponse(Long id, String username, Role role) {
}

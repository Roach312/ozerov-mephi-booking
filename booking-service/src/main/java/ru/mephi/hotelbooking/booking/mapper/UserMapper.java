package ru.mephi.hotelbooking.booking.mapper;

import org.mapstruct.Mapper;
import ru.mephi.hotelbooking.booking.dto.UserResponse;
import ru.mephi.hotelbooking.booking.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}

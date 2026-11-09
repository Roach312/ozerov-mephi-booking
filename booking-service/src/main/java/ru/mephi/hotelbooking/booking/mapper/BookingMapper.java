package ru.mephi.hotelbooking.booking.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.mephi.hotelbooking.booking.dto.BookingResponse;
import ru.mephi.hotelbooking.booking.entity.Booking;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "userId", source = "user.id")
    BookingResponse toResponse(Booking booking);
}

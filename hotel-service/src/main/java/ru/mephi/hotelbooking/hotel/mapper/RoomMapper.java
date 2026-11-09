package ru.mephi.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.entity.Room;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);

    /** Ссылку на отель и значения по умолчанию (available, timesBooked) проставляет сервис. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    Room toEntity(RoomRequest request);
}

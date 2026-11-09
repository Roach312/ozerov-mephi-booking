package ru.mephi.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.mephi.hotelbooking.hotel.dto.AvailabilityResponse;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;

@Mapper(componentModel = "spring")
public interface SlotMapper {

    @Mapping(target = "roomId", source = "room.id")
    AvailabilityResponse toAvailability(RoomSlot slot);
}

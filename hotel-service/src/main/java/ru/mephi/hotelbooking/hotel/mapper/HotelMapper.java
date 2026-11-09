package ru.mephi.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;
import ru.mephi.hotelbooking.hotel.entity.Hotel;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true)
    Hotel toEntity(HotelRequest request);

    @Mapping(target = "id", ignore = true)
    void update(HotelRequest request, @MappingTarget Hotel hotel);
}

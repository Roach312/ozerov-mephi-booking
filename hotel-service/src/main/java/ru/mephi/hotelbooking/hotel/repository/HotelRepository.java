package ru.mephi.hotelbooking.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.hotelbooking.hotel.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
}

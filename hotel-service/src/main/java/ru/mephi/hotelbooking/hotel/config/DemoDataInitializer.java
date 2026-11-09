package ru.mephi.hotelbooking.hotel.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;

/** Заполняет in-memory базу несколькими отелями и номерами, чтобы система была пригодна к работе сразу после старта. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0) {
            return;
        }
        Hotel grand = hotelRepository.save(new Hotel("Гранд Отель", "Москва, ул. Тверская, 1"));
        Hotel seaside = hotelRepository.save(new Hotel("Приморский курорт", "Сочи, ул. Приморская, 10"));
        Hotel mountain = hotelRepository.save(new Hotel("Горный приют", "Красная Поляна, ул. Горная, 5"));

        roomRepository.saveAll(List.of(
                new Room(grand, "101", true),
                new Room(grand, "102", true),
                new Room(grand, "201", true),
                new Room(grand, "202", true),
                new Room(seaside, "1", true),
                new Room(seaside, "2", true),
                new Room(seaside, "3", true),
                new Room(mountain, "A1", true),
                new Room(mountain, "A2", true),
                new Room(mountain, "B1", true)));
        log.info("Созданы демо-данные: 3 отеля, 10 номеров");
    }
}

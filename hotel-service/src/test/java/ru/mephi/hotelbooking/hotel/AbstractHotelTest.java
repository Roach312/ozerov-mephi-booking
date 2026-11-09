package ru.mephi.hotelbooking.hotel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomSlotRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractHotelTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected HotelRepository hotelRepository;
    @Autowired
    protected RoomRepository roomRepository;
    @Autowired
    protected RoomSlotRepository slotRepository;

    @BeforeEach
    void cleanDatabase() {
        slotRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    protected Hotel newHotel(String name) {
        return hotelRepository.save(new Hotel(name, name + " address"));
    }

    protected Room newRoom(Hotel hotel, String number, int timesBooked, boolean available) {
        Room room = new Room(hotel, number, available);
        room.setTimesBooked(timesBooked);
        return roomRepository.save(room);
    }

    protected String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }
}

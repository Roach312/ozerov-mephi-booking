package ru.mephi.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Номер отеля.
 * <p>
 * {@code available} - операционная доступность номера (ремонт, вывод из эксплуатации). Она НЕ используется для
 * проверки занятости на конкретные даты: за это отвечают блокировки {@link RoomSlot}.
 * {@code timesBooked} - число действующих (HELD) блокировок номера, то есть статистика загруженности.
 */
@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(name = "uk_room_hotel_number", columnNames = {"hotel_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false, length = 32)
    private String number;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "times_booked", nullable = false)
    private int timesBooked;

    public Room(Hotel hotel, String number, boolean available) {
        this.hotel = hotel;
        this.number = number;
        this.available = available;
    }
}

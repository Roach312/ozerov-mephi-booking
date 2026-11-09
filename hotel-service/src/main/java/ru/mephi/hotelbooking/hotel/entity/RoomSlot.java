package ru.mephi.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Временная блокировка номера на период [startDate, endDate). Одновременно это журнал идемпотентности шага
 * согласования: одна запись на один корреляционный идентификатор ({@code requestId}).
 * <p>
 * Запись со статусом {@link SlotStatus#RELEASED} и без дат - "надгробие": она создаётся, когда {@code release}
 * приходит раньше (или без) соответствующего {@code confirm}. Тогда запоздавший {@code confirm} с тем же
 * requestId отклоняется и не оставляет "висящую" блокировку.
 */
@Entity
@Table(name = "room_slots",
        uniqueConstraints = @UniqueConstraint(name = "uk_slot_request_id", columnNames = "request_id"),
        indexes = @Index(name = "idx_slot_room_status", columnList = "room_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class RoomSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;
}

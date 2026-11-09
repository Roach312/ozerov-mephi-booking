package ru.mephi.hotelbooking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Бронирование номера на полуоткрытый период [startDate, endDate).
 * {@code requestId} - ключ идемпотентности, присланный клиентом: пара (пользователь, requestId) уникальна,
 * поэтому повторно доставленный запрос никогда не создаёт второе бронирование.
 */
@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_user_request", columnNames = {"user_id", "request_id"}),
        indexes = {
                @Index(name = "idx_booking_room_status", columnList = "room_id, status"),
                @Index(name = "idx_booking_status_created", columnList = "status, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    /** true, если компенсирующий release в сервисе отелей не удался и его нужно повторить в фоне. */
    @Column(name = "release_pending", nullable = false)
    private boolean releasePending;

    /** Ключ идемпотентности шага согласования в сервисе отелей для текущего номера. */
    public String hotelRequestId() {
        return id + ":" + roomId;
    }
}

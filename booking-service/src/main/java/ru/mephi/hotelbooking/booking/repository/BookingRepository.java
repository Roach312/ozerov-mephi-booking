package ru.mephi.hotelbooking.booking.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByUserIdAndRequestId(Long userId, String requestId);

    List<Booking> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Есть ли активное бронирование номера, пересекающееся с полуоткрытым периодом [start, end). */
    boolean existsByRoomIdAndStatusInAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId, Collection<BookingStatus> statuses, LocalDate endDate, LocalDate startDate);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant threshold);

    List<Booking> findByStatusAndReleasePendingTrue(BookingStatus status);

    void deleteByUserId(Long userId);

    /** Переход состояния по принципу compare-and-set: выполняется, только если бронирование всё ещё в ожидаемом статусе. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b
            set b.status = :to, b.failureReason = :reason, b.updatedAt = :now
            where b.id = :id and b.status = :from
            """)
    int transition(@Param("id") Long id,
                   @Param("from") BookingStatus from,
                   @Param("to") BookingStatus to,
                   @Param("reason") String reason,
                   @Param("now") Instant now);

    /** Переносит PENDING-бронирование на другой номер (автоподбор пробует следующего кандидата). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b
            set b.roomId = :roomId, b.updatedAt = :now
            where b.id = :id and b.status = ru.mephi.hotelbooking.booking.entity.BookingStatus.PENDING
            """)
    int reassignRoom(@Param("id") Long id, @Param("roomId") Long roomId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Booking b set b.releasePending = :pending, b.updatedAt = :now where b.id = :id")
    int setReleasePending(@Param("id") Long id, @Param("pending") boolean pending, @Param("now") Instant now);
}

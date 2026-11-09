package ru.mephi.hotelbooking.hotel.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.hotel.entity.RoomSlot;
import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

public interface RoomSlotRepository extends JpaRepository<RoomSlot, Long> {

    Optional<RoomSlot> findByRequestId(String requestId);

    /** Есть ли у номера слот с указанным статусом, пересекающийся с полуоткрытым периодом [start, end). */
    boolean existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId, SlotStatus status, LocalDate endDate, LocalDate startDate);

    boolean existsByRoomIdAndStatus(Long roomId, SlotStatus status);

    @Modifying
    @Query("delete from RoomSlot s where s.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);
}

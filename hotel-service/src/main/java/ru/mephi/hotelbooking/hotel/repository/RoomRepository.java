package ru.mephi.hotelbooking.hotel.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.hotel.dto.HotelLoadRow;
import ru.mephi.hotelbooking.hotel.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByHotelId(Long hotelId);

    boolean existsByHotelIdAndNumber(Long hotelId, String number);

    boolean existsByHotelIdAndNumberAndIdNot(Long hotelId, String number, Long id);

    /**
     * Загружает номер с пессимистичной блокировкой на запись (SELECT ... FOR UPDATE). Все операции со слотами
     * одного номера выполняются строго по очереди, поэтому "проверить и вставить" слот не подвержено гонкам.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    /** Операционно доступные номера (без фильтра по датам). */
    List<Room> findByAvailableTrue(Sort sort);

    /** Операционно доступные номера без HELD-слотов, пересекающихся с полуоткрытым периодом [start, end). */
    @Query("""
            select r from Room r
            where r.available = true
              and not exists (
                  select 1 from RoomSlot s
                  where s.room = r
                    and s.status = ru.mephi.hotelbooking.hotel.entity.SlotStatus.HELD
                    and s.startDate < :endDate
                    and s.endDate > :startDate)
            """)
    List<Room> findAvailableForPeriod(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate,
                                      Sort sort);

    /** Агрегированная загруженность по отелям (отели без номеров попадают в результат с нулями). */
    @Query("""
            select new ru.mephi.hotelbooking.hotel.dto.HotelLoadRow(
                h.id, h.name, count(r), coalesce(sum(r.timesBooked), 0L), coalesce(avg(r.timesBooked), 0.0))
            from Hotel h left join Room r on r.hotel = h
            group by h.id, h.name
            order by h.id
            """)
    List<HotelLoadRow> aggregateLoadByHotel();
}

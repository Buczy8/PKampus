package pl.edu.pk.pkampus.modules.laundry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LaundryBookingRepository extends JpaRepository<LaundryBooking, UUID> {

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine m
            JOIN FETCH b.user
            WHERE m.dormitory.id = :dormitoryId
              AND b.status IN :statuses
              AND b.startTime < :rangeEnd
              AND b.endTime > :rangeStart
            """)
    List<LaundryBooking> findActiveInRange(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") Collection<LaundryBookingStatus> statuses
    );

    @Query("""
            SELECT COUNT(b) FROM LaundryBooking b
            WHERE b.user.id = :userId
              AND b.status IN :statuses
              AND b.startTime >= :weekStart
              AND b.startTime < :weekEnd
            """)
    long countActiveInWeek(
            @Param("userId") UUID userId,
            @Param("weekStart") Instant weekStart,
            @Param("weekEnd") Instant weekEnd,
            @Param("statuses") Collection<LaundryBookingStatus> statuses
    );

    @Query("""
            SELECT COUNT(b) > 0 FROM LaundryBooking b
            WHERE b.machine.id = :machineId
              AND b.status IN :statuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    boolean existsOverlapping(
            @Param("machineId") UUID machineId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("statuses") Collection<LaundryBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine
            WHERE b.user.id = :userId
              AND b.status IN :statuses
            ORDER BY b.startTime ASC
            """)
    List<LaundryBooking> findByUserIdAndStatusInOrderByStartTimeAsc(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<LaundryBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine
            JOIN FETCH b.user
            WHERE b.id = :id AND b.user.id = :userId
            """)
    Optional<LaundryBooking> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}

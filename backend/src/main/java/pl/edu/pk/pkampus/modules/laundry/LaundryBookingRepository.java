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
              AND b.startTime >= :rangeStart
              AND b.startTime < :rangeEnd
            """)
    long countActiveStartingBetween(
            @Param("userId") UUID userId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
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

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine m
            JOIN FETCH m.dormitory
            JOIN FETCH b.user
            WHERE m.dormitory.id = :dormitoryId
              AND b.status IN :statuses
              AND b.startTime >= :dayStart
              AND b.startTime < :dayEnd
            ORDER BY b.startTime ASC
            """)
    List<LaundryBooking> findDeskBookingsForDormitoryDay(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            @Param("statuses") Collection<LaundryBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine m
            JOIN FETCH m.dormitory
            JOIN FETCH b.user
            WHERE b.id = :id
            """)
    Optional<LaundryBooking> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.user
            JOIN FETCH b.machine
            WHERE b.machine.id = :machineId
              AND b.status = :status
              AND b.startTime > :now
            ORDER BY b.startTime ASC
            """)
    List<LaundryBooking> findFutureByMachineIdAndStatus(
            @Param("machineId") UUID machineId,
            @Param("status") LaundryBookingStatus status,
            @Param("now") Instant now
    );

    @Query("""
            SELECT b FROM LaundryBooking b
            JOIN FETCH b.machine m
            JOIN FETCH m.dormitory
            JOIN FETCH b.user
            WHERE b.status = :status
              AND b.startTime <= :cutoff
            """)
    List<LaundryBooking> findExpiredUnclaimed(
            @Param("status") LaundryBookingStatus status,
            @Param("cutoff") Instant cutoff
    );

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("""
            DELETE FROM LaundryBooking b
            WHERE b.status IN :statuses
              AND b.endTime < :cutoff
            """)
    int deleteOldCompletedOrCancelledBookings(
            @Param("statuses") Collection<LaundryBookingStatus> statuses,
            @Param("cutoff") Instant cutoff
    );
}


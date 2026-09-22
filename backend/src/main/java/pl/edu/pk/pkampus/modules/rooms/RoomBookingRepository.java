package pl.edu.pk.pkampus.modules.rooms;

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
public interface RoomBookingRepository extends JpaRepository<RoomBooking, UUID> {

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room r
            WHERE r.id = :roomId
              AND b.status IN :statuses
              AND b.startTime < :rangeEnd
              AND b.endTime > :rangeStart
            ORDER BY b.startTime ASC
            """)
    List<RoomBooking> findActiveForRoomInRange(
            @Param("roomId") UUID roomId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT COUNT(b) > 0 FROM RoomBooking b
            WHERE b.room.id = :roomId
              AND b.status IN :statuses
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    boolean existsOverlapping(
            @Param("roomId") UUID roomId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room
            WHERE b.user.id = :userId
              AND b.status IN :statuses
            ORDER BY b.startTime ASC
            """)
    List<RoomBooking> findByUserIdAndStatusInOrderByStartTimeAsc(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT COUNT(b) > 0 FROM RoomBooking b
            WHERE b.user.id = :userId
              AND b.status IN :statuses
              AND b.endTime > :now
              AND b.startTime < :dayEnd
              AND b.endTime > :dayStart
            """)
    boolean existsActiveNotEndedForUserOnDay(
            @Param("userId") UUID userId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            @Param("now") Instant now,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room
            JOIN FETCH b.user
            WHERE b.id = :id AND b.user.id = :userId
            """)
    Optional<RoomBooking> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room r
            JOIN FETCH r.dormitory
            JOIN FETCH b.user
            WHERE r.dormitory.id = :dormitoryId
              AND b.status IN :statuses
              AND b.startTime >= :dayStart
              AND b.startTime < :dayEnd
            ORDER BY b.startTime ASC
            """)
    List<RoomBooking> findDeskBookingsForDormitoryDay(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room r
            JOIN FETCH r.dormitory
            JOIN FETCH b.user
            WHERE b.id = :id
            """)
    Optional<RoomBooking> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room r
            JOIN FETCH b.user
            WHERE r.dormitory.id = :dormitoryId
              AND b.status IN :statuses
              AND b.startTime < :rangeEnd
              AND b.endTime > :rangeStart
            ORDER BY b.startTime ASC
            """)
    List<RoomBooking> findActiveInDormitoryRange(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") Collection<RoomBookingStatus> statuses
    );

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.user
            JOIN FETCH b.room
            WHERE b.room.id = :roomId
              AND b.status = :status
              AND b.startTime > :now
            ORDER BY b.startTime ASC
            """)
    List<RoomBooking> findFutureByRoomIdAndStatus(
            @Param("roomId") UUID roomId,
            @Param("status") RoomBookingStatus status,
            @Param("now") Instant now
    );

    @Query("""
            SELECT b FROM RoomBooking b
            JOIN FETCH b.room r
            JOIN FETCH r.dormitory
            JOIN FETCH b.user
            WHERE b.status = :status
              AND b.startTime <= :cutoff
            """)
    List<RoomBooking> findExpiredUnclaimed(
            @Param("status") RoomBookingStatus status,
            @Param("cutoff") Instant cutoff
    );
}

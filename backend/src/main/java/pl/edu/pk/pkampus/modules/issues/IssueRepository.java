package pl.edu.pk.pkampus.modules.issues;

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
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    @Query("""
            SELECT DISTINCT i FROM Issue i
            LEFT JOIN FETCH i.room
            LEFT JOIN FETCH i.photos
            WHERE i.reporter.id = :reporterId
            ORDER BY i.createdAt DESC
            """)
    List<Issue> findByReporterIdWithDetailsOrderByCreatedAtDesc(@Param("reporterId") UUID reporterId);

    @Query("""
            SELECT i FROM Issue i
            LEFT JOIN FETCH i.room
            WHERE i.dormitory.id = :dormitoryId
              AND i.status IN :statuses
            ORDER BY i.createdAt DESC
            """)
    List<Issue> findByDormitoryIdAndStatusInOrderByCreatedAtDesc(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("statuses") Collection<IssueStatus> statuses
    );

    @Query("""
            SELECT DISTINCT i FROM Issue i
            LEFT JOIN FETCH i.room r
            LEFT JOIN FETCH i.photos
            JOIN FETCH i.reporter
            WHERE i.dormitory.id = :dormitoryId
              AND (:statusesEmpty = true OR i.status IN :statuses)
              AND (:categoryEmpty = true OR i.category = :category)
              AND (:urgencyEmpty = true OR i.urgency = :urgency)
              AND (:createdFromEmpty = true OR i.createdAt >= :createdFrom)
              AND (:createdToEmpty = true OR i.createdAt < :createdTo)
              AND (:roomNumberEmpty = true OR LOWER(r.roomNumber) = :roomNumber)
              AND (:floorEmpty = true OR r.floor = :floor)
            ORDER BY i.createdAt DESC
            """)
    List<Issue> findStaffFiltered(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("statusesEmpty") boolean statusesEmpty,
            @Param("statuses") Collection<IssueStatus> statuses,
            @Param("categoryEmpty") boolean categoryEmpty,
            @Param("category") IssueCategory category,
            @Param("urgencyEmpty") boolean urgencyEmpty,
            @Param("urgency") IssueUrgency urgency,
            @Param("createdFromEmpty") boolean createdFromEmpty,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdToEmpty") boolean createdToEmpty,
            @Param("createdTo") Instant createdTo,
            @Param("roomNumberEmpty") boolean roomNumberEmpty,
            @Param("roomNumber") String roomNumber,
            @Param("floorEmpty") boolean floorEmpty,
            @Param("floor") Integer floor
    );

    @Query("""
            SELECT i FROM Issue i
            LEFT JOIN FETCH i.room
            LEFT JOIN FETCH i.photos
            JOIN FETCH i.reporter
            WHERE i.id = :id AND i.dormitory.id = :dormitoryId
            """)
    Optional<Issue> findByIdAndDormitoryIdWithDetails(
            @Param("id") UUID id,
            @Param("dormitoryId") UUID dormitoryId
    );

    @Query("""
            SELECT i FROM Issue i
            LEFT JOIN FETCH i.room
            LEFT JOIN FETCH i.photos
            WHERE i.id = :id AND i.reporter.id = :reporterId
            """)
    Optional<Issue> findByIdAndReporterIdWithDetails(
            @Param("id") UUID id,
            @Param("reporterId") UUID reporterId
    );
}

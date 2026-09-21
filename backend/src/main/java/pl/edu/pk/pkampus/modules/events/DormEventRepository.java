package pl.edu.pk.pkampus.modules.events;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DormEventRepository extends JpaRepository<DormEvent, UUID> {

    @EntityGraph(attributePaths = {"author", "dormitory"})
    List<DormEvent> findAllByDormitoryIsNullOrderByEventDateDesc();

    @EntityGraph(attributePaths = {"author", "dormitory"})
    List<DormEvent> findAllByDormitoryIdOrderByEventDateDesc(UUID dormitoryId);

    @EntityGraph(attributePaths = {"author", "dormitory"})
    Optional<DormEvent> findByIdAndDormitoryId(UUID id, UUID dormitoryId);

    @EntityGraph(attributePaths = {"author", "dormitory"})
    @Query("""
            SELECT e FROM DormEvent e
            WHERE (e.dormitory.id = :dormitoryId OR e.dormitory IS NULL)
              AND e.eventDate <= :now
              AND (e.endDate IS NULL OR e.endDate >= :now)
            ORDER BY e.eventDate DESC
            """)
    List<DormEvent> findVisibleForDormitory(
            @Param("dormitoryId") UUID dormitoryId,
            @Param("now") Instant now
    );

    @EntityGraph(attributePaths = {"author", "dormitory"})
    @Query("""
            SELECT e FROM DormEvent e
            WHERE e.dormitory IS NULL
              AND e.eventDate <= :now
              AND (e.endDate IS NULL OR e.endDate >= :now)
            ORDER BY e.eventDate DESC
            """)
    List<DormEvent> findVisibleCampusOnly(@Param("now") Instant now);

    @Query("""
            SELECT e FROM DormEvent e
            WHERE e.pinned = TRUE
              AND e.priority = pl.edu.pk.pkampus.modules.events.DormEventPriority.CRITICAL
              AND e.eventDate <= :now
              AND (e.endDate IS NULL OR e.endDate >= :now)
              AND (e.dormitory IS NULL OR e.dormitory.id = :dormitoryId)
            ORDER BY e.eventDate DESC
            """)
    List<DormEvent> findActiveBannerCandidates(
            @Param("now") Instant now,
            @Param("dormitoryId") UUID dormitoryId
    );

    @Query("""
            SELECT e FROM DormEvent e
            WHERE e.pinned = TRUE
              AND e.priority = pl.edu.pk.pkampus.modules.events.DormEventPriority.CRITICAL
              AND e.eventDate <= :now
              AND (e.endDate IS NULL OR e.endDate >= :now)
              AND e.dormitory IS NULL
            ORDER BY e.eventDate DESC
            """)
    List<DormEvent> findActiveCampusBannerCandidates(@Param("now") Instant now);
}

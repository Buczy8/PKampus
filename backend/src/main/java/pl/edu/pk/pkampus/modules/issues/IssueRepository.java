package pl.edu.pk.pkampus.modules.issues;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

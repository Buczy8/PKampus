package pl.edu.pk.pkampus.modules.issues;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface IssuePhotoRepository extends JpaRepository<IssuePhoto, UUID> {

    @Query("""
            SELECT p FROM IssuePhoto p
            JOIN FETCH p.issue i
            WHERE i.status IN :statuses
              AND i.updatedAt < :cutoff
            """)
    List<IssuePhoto> findPhotosForOldClosedIssues(
            @Param("statuses") Collection<IssueStatus> statuses,
            @Param("cutoff") Instant cutoff
    );
}

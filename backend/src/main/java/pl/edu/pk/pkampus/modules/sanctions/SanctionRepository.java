package pl.edu.pk.pkampus.modules.sanctions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SanctionRepository extends JpaRepository<Sanction, UUID> {

    @Query("""
            SELECT s FROM Sanction s
            WHERE s.user.id = :userId
              AND s.sanctionType = :type
              AND s.active = TRUE
              AND s.endDate >= :today
            ORDER BY s.endDate DESC
            """)
    List<Sanction> findActiveByUserAndType(
            @Param("userId") UUID userId,
            @Param("type") SanctionType type,
            @Param("today") LocalDate today
    );

    Optional<Sanction> findByIdAndUserId(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT s FROM Sanction s
            WHERE s.user.id IN :userIds
              AND s.sanctionType = pl.edu.pk.pkampus.modules.sanctions.SanctionType.ROOM_BAN
              AND s.active = TRUE
              AND s.endDate >= :today
            """)
    List<Sanction> findActiveRoomBansForUsers(
            @Param("userIds") List<UUID> userIds,
            @Param("today") LocalDate today
    );
}

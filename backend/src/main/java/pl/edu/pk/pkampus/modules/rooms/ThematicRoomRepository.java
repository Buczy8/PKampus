package pl.edu.pk.pkampus.modules.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThematicRoomRepository extends JpaRepository<ThematicRoom, UUID> {

    List<ThematicRoom> findAllByDormitoryIdOrderByNameAsc(UUID dormitoryId);

    List<ThematicRoom> findAllByDormitoryIdAndStatusOrderByNameAsc(UUID dormitoryId, ThematicRoomStatus status);

    Optional<ThematicRoom> findByIdAndDormitoryId(UUID id, UUID dormitoryId);
}

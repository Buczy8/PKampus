package pl.edu.pk.pkampus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.edu.pk.pkampus.model.Room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByDormitoryIdAndRoomNumber(UUID dormitoryId, String roomNumber);

    List<Room> findAllByDormitoryId(UUID dormitoryId);

    boolean existsByDormitoryIdAndRoomNumber(UUID dormitoryId, String roomNumber);
}

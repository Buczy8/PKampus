package pl.edu.pk.pkampus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.edu.pk.pkampus.model.Dormitory;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DormitoryRepository extends JpaRepository<Dormitory, UUID> {

    Optional<Dormitory> findByCode(String code);
}

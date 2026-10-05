package pl.edu.pk.pkampus.modules.dormitory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DormitoryRepository extends JpaRepository<Dormitory, UUID> {

    Optional<Dormitory> findByCode(String code);

    List<Dormitory> findAllByOrderByNameAsc();
}

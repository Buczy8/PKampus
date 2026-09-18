package pl.edu.pk.pkampus.modules.laundry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LaundryMachineRepository extends JpaRepository<LaundryMachine, UUID> {

    List<LaundryMachine> findAllByDormitoryIdOrderByMachineIdentifierAsc(UUID dormitoryId);

    Optional<LaundryMachine> findByIdAndDormitoryId(UUID id, UUID dormitoryId);

    boolean existsByDormitoryIdAndMachineIdentifierIgnoreCase(UUID dormitoryId, String machineIdentifier);

    boolean existsByDormitoryIdAndMachineIdentifierIgnoreCaseAndIdNot(
            UUID dormitoryId,
            String machineIdentifier,
            UUID id
    );
}

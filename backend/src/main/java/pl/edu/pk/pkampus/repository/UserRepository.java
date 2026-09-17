package pl.edu.pk.pkampus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.edu.pk.pkampus.model.User;
import pl.edu.pk.pkampus.model.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByDormitoryIdAndStatus(UUID dormitoryId, UserStatus status);

    List<User> findAllByStatus(UserStatus status);
}

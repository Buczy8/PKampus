package pl.edu.pk.pkampus.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    List<User> findAllByDormitoryIdAndStatus(UUID dormitoryId, UserStatus status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    List<User> findAllByStatus(UserStatus status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    List<User> findAllByRoleOrderByLastNameAscFirstNameAsc(UserRole role);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    List<User> findAllByDormitoryIdAndRoleOrderByLastNameAscFirstNameAsc(UUID dormitoryId, UserRole role);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    Optional<User> findByIdAndDormitoryIdAndRole(UUID id, UUID dormitoryId, UserRole role);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"dormitory"})
    @Override
    Optional<User> findById(UUID id);
}

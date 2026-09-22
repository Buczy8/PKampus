package pl.edu.pk.pkampus.modules.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.admin.dto.CreateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.admin.dto.ReceptionistDto;
import pl.edu.pk.pkampus.modules.admin.dto.UpdateReceptionistRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminReceptionistService unit tests (AAA)")
class AdminReceptionistServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AdminReceptionistService adminReceptionistService;

    private Dormitory dormitory;
    private User admin;
    private User receptionist;
    private UUID receptionistId;

    @BeforeEach
    void setUp() {
        dormitory = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Portier Test")
                .build();

        admin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .dormitory(dormitory)
                .build();

        receptionistId = UUID.randomUUID();
        receptionist = User.builder()
                .id(receptionistId)
                .email("portier@pk.edu.pl")
                .firstName("Stanisław")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .build();
    }

    @Nested
    @DisplayName("list")
    class ListReceptionists {

        @Test
        @DisplayName("Should return list of receptionists for admin's dormitory")
        void listSuccess() {
            // Arrange
            when(userRepository.findAllByDormitoryIdAndRoleOrderByLastNameAscFirstNameAsc(
                    dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(List.of(receptionist));

            // Act
            List<ReceptionistDto> result = adminReceptionistService.list(admin);

            // Assert
            assertEquals(1, result.size());
            ReceptionistDto dto = result.getFirst();
            assertEquals(receptionistId, dto.getId());
            assertEquals("portier@pk.edu.pl", dto.getEmail());
            assertEquals("Stanisław", dto.getFirstName());
            assertEquals("Kowalski", dto.getLastName());
            assertEquals(dormitory.getId(), dto.getDormitoryId());
            assertEquals(dormitory.getName(), dto.getDormitoryName());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when role is not DORM_ADMIN")
        void listThrowsWhenNotDormAdmin() {
            // Arrange
            User nonAdmin = User.builder()
                    .role(UserRole.RESIDENT)
                    .dormitory(dormitory)
                    .build();

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> adminReceptionistService.list(nonAdmin));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when admin has no dormitory")
        void listThrowsWhenAdminHasNoDormitory() {
            // Arrange
            User noDormAdmin = User.builder()
                    .role(UserRole.DORM_ADMIN)
                    .dormitory(null)
                    .build();

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> adminReceptionistService.list(noDormAdmin));
        }
    }

    @Nested
    @DisplayName("create")
    class CreateReceptionist {

        @Test
        @DisplayName("Should create receptionist successfully with MUST_CHANGE_PASSWORD status")
        void createSuccess() {
            // Arrange
            CreateReceptionistRequestDto request = CreateReceptionistRequestDto.builder()
                    .firstName("  Jan  ")
                    .lastName("  Nowak  ")
                    .email("  JAN.NOWAK@PK.EDU.PL  ")
                    .phoneNumber("  +48999888777  ")
                    .password("RawPassword123!")
                    .build();

            when(userRepository.existsByEmail("jan.nowak@pk.edu.pl")).thenReturn(false);
            when(passwordEncoder.encode("RawPassword123!")).thenReturn("encodedHash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            // Act
            ReceptionistDto result = adminReceptionistService.create(admin, request);

            // Assert
            assertNotNull(result);
            assertEquals("jan.nowak@pk.edu.pl", result.getEmail());
            assertEquals("Jan", result.getFirstName());
            assertEquals("Nowak", result.getLastName());
            assertEquals("+48999888777", result.getPhoneNumber());
            assertEquals(UserStatus.MUST_CHANGE_PASSWORD, result.getStatus());
            assertEquals(dormitory.getId(), result.getDormitoryId());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertEquals("jan.nowak@pk.edu.pl", saved.getEmail());
            assertEquals("encodedHash", saved.getPasswordHash());
            assertEquals(UserRole.RECEPTIONIST, saved.getRole());
            assertEquals(UserStatus.MUST_CHANGE_PASSWORD, saved.getStatus());
            assertEquals(dormitory, saved.getDormitory());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when email already exists")
        void createThrowsWhenEmailExists() {
            // Arrange
            CreateReceptionistRequestDto request = CreateReceptionistRequestDto.builder()
                    .email("existing@pk.edu.pl")
                    .password("Password123!")
                    .firstName("Jan")
                    .lastName("Nowak")
                    .phoneNumber("+48111222333")
                    .build();

            when(userRepository.existsByEmail("existing@pk.edu.pl")).thenReturn(true);

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> adminReceptionistService.create(admin, request));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when creator is not DORM_ADMIN")
        void createThrowsWhenNotDormAdmin() {
            // Arrange
            User resident = User.builder().role(UserRole.RESIDENT).dormitory(dormitory).build();
            CreateReceptionistRequestDto request = CreateReceptionistRequestDto.builder().build();

            // Act & Assert
            assertThrows(AccessDeniedException.class, () -> adminReceptionistService.create(resident, request));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateReceptionist {

        @Test
        @DisplayName("Should update fields and revoke tokens when status changed to BLOCKED")
        void updateStatusToBlockedRevokesTokens() {
            // Arrange
            UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                    .firstName("  Janusz  ")
                    .lastName("  Nowicki  ")
                    .phoneNumber("  +48111000111  ")
                    .status(UserStatus.BLOCKED)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(receptionistId, dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(Optional.of(receptionist));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ReceptionistDto result = adminReceptionistService.update(admin, receptionistId, request);

            // Assert
            assertEquals("Janusz", result.getFirstName());
            assertEquals("Nowicki", result.getLastName());
            assertEquals("+48111000111", result.getPhoneNumber());
            assertEquals(UserStatus.BLOCKED, result.getStatus());
            verify(tokenRevocationService).revokeUser(receptionistId);
            verify(tokenRevocationService, never()).clearRevocation(any());
        }

        @Test
        @DisplayName("Should update status to ACTIVE and clear revocation")
        void updateStatusToActiveClearsRevocation() {
            // Arrange
            receptionist.setStatus(UserStatus.BLOCKED);
            UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(receptionistId, dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(Optional.of(receptionist));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ReceptionistDto result = adminReceptionistService.update(admin, receptionistId, request);

            // Assert
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            verify(tokenRevocationService).clearRevocation(receptionistId);
            verify(tokenRevocationService, never()).revokeUser(any());
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when requested status is not ACTIVE or BLOCKED")
        void updateThrowsOnInvalidStatus() {
            // Arrange
            UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                    .status(UserStatus.PENDING_APPROVAL)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(receptionistId, dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(Optional.of(receptionist));

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> adminReceptionistService.update(admin, receptionistId, request));
            assertEquals("Status must be ACTIVE or BLOCKED", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when receptionist not found in dormitory")
        void updateThrowsWhenNotFound() {
            // Arrange
            UUID notFoundId = UUID.randomUUID();
            UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder().build();

            when(userRepository.findByIdAndDormitoryIdAndRole(notFoundId, dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class,
                    () -> adminReceptionistService.update(admin, notFoundId, request));
        }

        @Test
        @DisplayName("Should ignore blank/null fields during partial update")
        void updateIgnoresBlankFields() {
            // Arrange
            UpdateReceptionistRequestDto request = UpdateReceptionistRequestDto.builder()
                    .firstName("   ")
                    .lastName(null)
                    .phoneNumber("")
                    .status(null)
                    .build();

            when(userRepository.findByIdAndDormitoryIdAndRole(receptionistId, dormitory.getId(), UserRole.RECEPTIONIST))
                    .thenReturn(Optional.of(receptionist));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ReceptionistDto result = adminReceptionistService.update(admin, receptionistId, request);

            // Assert
            assertEquals("Stanisław", result.getFirstName());
            assertEquals("Kowalski", result.getLastName());
            assertEquals("+48123456789", result.getPhoneNumber());
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            verify(tokenRevocationService, never()).revokeUser(any());
            verify(tokenRevocationService, never()).clearRevocation(any());
        }
    }
}

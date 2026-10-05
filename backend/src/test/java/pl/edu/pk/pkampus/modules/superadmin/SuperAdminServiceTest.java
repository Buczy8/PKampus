package pl.edu.pk.pkampus.modules.superadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.events.DormEvent;
import pl.edu.pk.pkampus.modules.events.DormEventCategory;
import pl.edu.pk.pkampus.modules.events.DormEventPriority;
import pl.edu.pk.pkampus.modules.events.DormEventRepository;
import pl.edu.pk.pkampus.modules.events.dto.DormEventDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.CreateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.DormAdminDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.SuperAdminDormitoryDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateCampusEventRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormAdminRequestDto;
import pl.edu.pk.pkampus.modules.superadmin.dto.UpdateDormitoryRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminService unit tests")
class SuperAdminServiceTest {

    @Mock
    private DormitoryRepository dormitoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DormEventRepository dormEventRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private SuperAdminService superAdminService;

    @Nested
    @DisplayName("listDormitories")
    class ListDormitoriesTests {

        @Test
        @DisplayName("Returns list of mapped SuperAdminDormitoryDto")
        void returnsListOfDormitories() {
            // Arrange
            Dormitory dorm = Dormitory.builder()
                    .id(UUID.randomUUID())
                    .code("DS1")
                    .name("Dorm 1")
                    .address("ul. Testowa 1")
                    .floorsCount(4)
                    .laundryOpeningTime(LocalTime.of(7, 0))
                    .laundryClosingTime(LocalTime.of(23, 0))
                    .laundrySlotDurationMinutes(180)
                    .createdAt(Instant.now())
                    .build();
            when(dormitoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dorm));

            // Act
            List<SuperAdminDormitoryDto> result = superAdminService.listDormitories();

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(dorm.getId());
            assertThat(result.get(0).getCode()).isEqualTo("DS1");
            assertThat(result.get(0).getName()).isEqualTo("Dorm 1");
            assertThat(result.get(0).getLaundrySlotDurationMinutes()).isEqualTo(180);
        }
    }

    @Nested
    @DisplayName("createDormitory")
    class CreateDormitoryTests {

        @Test
        @DisplayName("Throws BusinessRuleException when dormitory code already exists")
        void throwsExceptionWhenCodeAlreadyExists() {
            // Arrange
            CreateDormitoryRequestDto request = CreateDormitoryRequestDto.builder()
                    .code("ds1 ")
                    .name("Dorm 1")
                    .address("ul. Testowa 1")
                    .floorsCount(4)
                    .build();
            when(dormitoryRepository.findByCode("DS1")).thenReturn(Optional.of(new Dormitory()));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.createDormitory(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Dormitory code already exists: DS1");
            verify(dormitoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Creates dormitory with default laundry settings and trimmed uppercase code")
        void createsDormitorySuccessfully() {
            // Arrange
            CreateDormitoryRequestDto request = CreateDormitoryRequestDto.builder()
                    .code(" ds2 ")
                    .name(" Dorm 2 ")
                    .address(" ul. Testowa 2 ")
                    .floorsCount(5)
                    .build();
            when(dormitoryRepository.findByCode("DS2")).thenReturn(Optional.empty());

            UUID dormId = UUID.randomUUID();
            Instant now = Instant.now();
            when(dormitoryRepository.save(any(Dormitory.class))).thenAnswer(invocation -> {
                Dormitory toSave = invocation.getArgument(0);
                toSave.setId(dormId);
                toSave.setCreatedAt(now);
                return toSave;
            });

            // Act
            SuperAdminDormitoryDto result = superAdminService.createDormitory(request);

            // Assert
            ArgumentCaptor<Dormitory> captor = ArgumentCaptor.forClass(Dormitory.class);
            verify(dormitoryRepository).save(captor.capture());
            Dormitory saved = captor.getValue();

            assertThat(saved.getCode()).isEqualTo("DS2");
            assertThat(saved.getName()).isEqualTo("Dorm 2");
            assertThat(saved.getAddress()).isEqualTo("ul. Testowa 2");
            assertThat(saved.getFloorsCount()).isEqualTo(5);
            assertThat(saved.getLaundryOpeningTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(saved.getLaundryClosingTime()).isEqualTo(LocalTime.of(23, 0));
            assertThat(saved.getLaundrySlotDurationMinutes()).isEqualTo(180);

            assertThat(result.getId()).isEqualTo(dormId);
            assertThat(result.getCode()).isEqualTo("DS2");
            assertThat(result.getName()).isEqualTo("Dorm 2");
        }
    }

    @Nested
    @DisplayName("updateDormitory")
    class UpdateDormitoryTests {

        @Test
        @DisplayName("Throws ResourceNotFoundException when dormitory not found")
        void throwsExceptionWhenNotFound() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            UpdateDormitoryRequestDto request = UpdateDormitoryRequestDto.builder().build();
            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormitory(dormId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dormitory not found");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when updating code to an already existing one")
        void throwsExceptionWhenDuplicateCode() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            UUID otherDormId = UUID.randomUUID();
            Dormitory existing = Dormitory.builder().id(dormId).code("DS1").build();
            Dormitory other = Dormitory.builder().id(otherDormId).code("DS2").build();

            UpdateDormitoryRequestDto request = UpdateDormitoryRequestDto.builder().code("ds2").build();
            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.of(existing));
            when(dormitoryRepository.findByCode("DS2")).thenReturn(Optional.of(other));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormitory(dormId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Dormitory code already exists: DS2");
            verify(dormitoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws BusinessRuleException when laundry opening time is not before closing time")
        void throwsExceptionWhenLaundryTimesInvalid() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            Dormitory existing = Dormitory.builder()
                    .id(dormId)
                    .code("DS1")
                    .laundryOpeningTime(LocalTime.of(7, 0))
                    .laundryClosingTime(LocalTime.of(23, 0))
                    .build();

            UpdateDormitoryRequestDto request = UpdateDormitoryRequestDto.builder()
                    .laundryOpeningTime(LocalTime.of(23, 0))
                    .laundryClosingTime(LocalTime.of(7, 0))
                    .build();
            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.of(existing));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormitory(dormId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Laundry opening time must be before closing time");
        }

        @Test
        @DisplayName("Updates all fields successfully when valid")
        void updatesAllFieldsSuccessfully() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            Dormitory existing = Dormitory.builder()
                    .id(dormId)
                    .code("DS1")
                    .name("Old Name")
                    .address("Old Address")
                    .floorsCount(3)
                    .laundryOpeningTime(LocalTime.of(7, 0))
                    .laundryClosingTime(LocalTime.of(23, 0))
                    .laundrySlotDurationMinutes(180)
                    .build();

            UpdateDormitoryRequestDto request = UpdateDormitoryRequestDto.builder()
                    .code(" DS1-NEW ")
                    .name(" New Name ")
                    .address(" New Address ")
                    .floorsCount(6)
                    .laundryOpeningTime(LocalTime.of(8, 0))
                    .laundryClosingTime(LocalTime.of(22, 0))
                    .laundrySlotDurationMinutes(120)
                    .build();

            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.of(existing));
            when(dormitoryRepository.findByCode("DS1-NEW")).thenReturn(Optional.empty());
            when(dormitoryRepository.save(any(Dormitory.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            SuperAdminDormitoryDto result = superAdminService.updateDormitory(dormId, request);

            // Assert
            assertThat(result.getCode()).isEqualTo("DS1-NEW");
            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getAddress()).isEqualTo("New Address");
            assertThat(result.getFloorsCount()).isEqualTo(6);
            assertThat(result.getLaundryOpeningTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(result.getLaundryClosingTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(result.getLaundrySlotDurationMinutes()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("listDormAdmins")
    class ListDormAdminsTests {

        @Test
        @DisplayName("Returns list of mapped DormAdminDto")
        void returnsListOfDormAdmins() {
            // Arrange
            Dormitory dorm = Dormitory.builder().id(UUID.randomUUID()).name("DS1").code("DS1").build();
            User admin = User.builder()
                    .id(UUID.randomUUID())
                    .email("admin@pk.edu.pl")
                    .firstName("Adam")
                    .lastName("Nowak")
                    .phoneNumber("+48123456789")
                    .role(UserRole.DORM_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .dormitory(dorm)
                    .createdAt(Instant.now())
                    .build();

            when(userRepository.findAllByRoleOrderByLastNameAscFirstNameAsc(UserRole.DORM_ADMIN))
                    .thenReturn(List.of(admin));

            // Act
            List<DormAdminDto> result = superAdminService.listDormAdmins();

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(admin.getId());
            assertThat(result.get(0).getEmail()).isEqualTo("admin@pk.edu.pl");
            assertThat(result.get(0).getDormitoryName()).isEqualTo("DS1");
        }
    }

    @Nested
    @DisplayName("createDormAdmin")
    class CreateDormAdminTests {

        @Test
        @DisplayName("Throws BusinessRuleException when email is already registered")
        void throwsExceptionWhenEmailExists() {
            // Arrange
            CreateDormAdminRequestDto request = CreateDormAdminRequestDto.builder()
                    .email("taken@pk.edu.pl")
                    .build();
            when(userRepository.existsByEmail("taken@pk.edu.pl")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.createDormAdmin(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Email is already registered");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when dormitory not found")
        void throwsExceptionWhenDormitoryNotFound() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            CreateDormAdminRequestDto request = CreateDormAdminRequestDto.builder()
                    .email("new@pk.edu.pl")
                    .dormitoryId(dormId)
                    .build();
            when(userRepository.existsByEmail("new@pk.edu.pl")).thenReturn(false);
            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.createDormAdmin(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dormitory not found");
        }

        @Test
        @DisplayName("Creates dorm admin with MUST_CHANGE_PASSWORD status and hashed password")
        void createsDormAdminSuccessfully() {
            // Arrange
            UUID dormId = UUID.randomUUID();
            Dormitory dorm = Dormitory.builder().id(dormId).code("DS1").name("DS 1").build();
            CreateDormAdminRequestDto request = CreateDormAdminRequestDto.builder()
                    .firstName(" Anna ")
                    .lastName(" Kowalska ")
                    .email(" Anna.Kowalska@PK.EDU.PL ")
                    .phoneNumber(" +48111222333 ")
                    .password("Secret123!")
                    .dormitoryId(dormId)
                    .build();

            when(userRepository.existsByEmail("anna.kowalska@pk.edu.pl")).thenReturn(false);
            when(dormitoryRepository.findById(dormId)).thenReturn(Optional.of(dorm));
            when(passwordEncoder.encode("Secret123!")).thenReturn("hashed_secret");

            UUID createdId = UUID.randomUUID();
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(createdId);
                return u;
            });

            // Act
            DormAdminDto result = superAdminService.createDormAdmin(request);

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("anna.kowalska@pk.edu.pl");
            assertThat(saved.getPasswordHash()).isEqualTo("hashed_secret");
            assertThat(saved.getFirstName()).isEqualTo("Anna");
            assertThat(saved.getLastName()).isEqualTo("Kowalska");
            assertThat(saved.getPhoneNumber()).isEqualTo("+48111222333");
            assertThat(saved.getRole()).isEqualTo(UserRole.DORM_ADMIN);
            assertThat(saved.getStatus()).isEqualTo(UserStatus.MUST_CHANGE_PASSWORD);
            assertThat(saved.getDormitory()).isEqualTo(dorm);

            assertThat(result.getId()).isEqualTo(createdId);
            assertThat(result.getEmail()).isEqualTo("anna.kowalska@pk.edu.pl");
            assertThat(result.getStatus()).isEqualTo(UserStatus.MUST_CHANGE_PASSWORD);
            assertThat(result.getDormitoryCode()).isEqualTo("DS1");
        }
    }

    @Nested
    @DisplayName("updateDormAdmin")
    class UpdateDormAdminTests {

        @Test
        @DisplayName("Throws ResourceNotFoundException when user not found")
        void throwsExceptionWhenUserNotFound() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder().build();
            when(userRepository.findById(adminId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormAdmin(adminId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dormitory admin not found");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when user is not DORM_ADMIN")
        void throwsExceptionWhenNotDormAdmin() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).role(UserRole.RESIDENT).build();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder().build();
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormAdmin(userId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("User is not a dormitory administrator");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when status is not ACTIVE or BLOCKED")
        void throwsExceptionWhenStatusInvalid() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            User admin = User.builder().id(adminId).role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).build();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .status(UserStatus.MUST_CHANGE_PASSWORD)
                    .build();
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormAdmin(adminId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Status must be ACTIVE or BLOCKED");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when target dormitory not found")
        void throwsExceptionWhenNewDormitoryNotFound() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            UUID newDormId = UUID.randomUUID();
            User admin = User.builder().id(adminId).role(UserRole.DORM_ADMIN).build();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .dormitoryId(newDormId)
                    .build();
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(dormitoryRepository.findById(newDormId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateDormAdmin(adminId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Dormitory not found");
        }

        @Test
        @DisplayName("Setting status to BLOCKED revokes user tokens")
        void settingStatusToBlockedRevokesTokens() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            User admin = User.builder().id(adminId).role(UserRole.DORM_ADMIN).status(UserStatus.ACTIVE).build();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .status(UserStatus.BLOCKED)
                    .build();
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            DormAdminDto result = superAdminService.updateDormAdmin(adminId, request);

            // Assert
            assertThat(result.getStatus()).isEqualTo(UserStatus.BLOCKED);
            verify(tokenRevocationService).revokeUser(adminId);
            verify(tokenRevocationService, never()).clearRevocation(any());
        }

        @Test
        @DisplayName("Setting status to ACTIVE clears token revocation")
        void settingStatusToActiveClearsRevocation() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            User admin = User.builder().id(adminId).role(UserRole.DORM_ADMIN).status(UserStatus.BLOCKED).build();
            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .status(UserStatus.ACTIVE)
                    .build();
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            DormAdminDto result = superAdminService.updateDormAdmin(adminId, request);

            // Assert
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(tokenRevocationService).clearRevocation(adminId);
            verify(tokenRevocationService, never()).revokeUser(any());
        }

        @Test
        @DisplayName("Updates profile fields and dormitory successfully")
        void updatesProfileAndDormitorySuccessfully() {
            // Arrange
            UUID adminId = UUID.randomUUID();
            UUID newDormId = UUID.randomUUID();
            Dormitory oldDorm = Dormitory.builder().id(UUID.randomUUID()).code("OLD").build();
            Dormitory newDorm = Dormitory.builder().id(newDormId).code("NEW").build();
            User admin = User.builder()
                    .id(adminId)
                    .role(UserRole.DORM_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .dormitory(oldDorm)
                    .firstName("OldFirst")
                    .lastName("OldLast")
                    .phoneNumber("+48000000000")
                    .build();

            UpdateDormAdminRequestDto request = UpdateDormAdminRequestDto.builder()
                    .dormitoryId(newDormId)
                    .firstName(" NewFirst ")
                    .lastName(" NewLast ")
                    .phoneNumber(" +48111111111 ")
                    .build();

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(dormitoryRepository.findById(newDormId)).thenReturn(Optional.of(newDorm));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            DormAdminDto result = superAdminService.updateDormAdmin(adminId, request);

            // Assert
            assertThat(result.getFirstName()).isEqualTo("NewFirst");
            assertThat(result.getLastName()).isEqualTo("NewLast");
            assertThat(result.getPhoneNumber()).isEqualTo("+48111111111");
            assertThat(result.getDormitoryCode()).isEqualTo("NEW");
        }
    }

    @Nested
    @DisplayName("listCampusEvents")
    class ListCampusEventsTests {

        @Test
        @DisplayName("Returns list of campus events where dormitory is null")
        void returnsCampusEvents() {
            // Arrange
            User author = User.builder().id(UUID.randomUUID()).firstName("Super").lastName("Admin").build();
            DormEvent event = DormEvent.builder()
                    .id(UUID.randomUUID())
                    .author(author)
                    .dormitory(null)
                    .title("Campus Notice")
                    .description("Campus description")
                    .category(DormEventCategory.ADMIN_NOTICE)
                    .priority(DormEventPriority.INFO)
                    .pinned(true)
                    .eventDate(Instant.now())
                    .build();

            when(dormEventRepository.findAllByDormitoryIsNullOrderByEventDateDesc())
                    .thenReturn(List.of(event));

            // Act
            List<DormEventDto> result = superAdminService.listCampusEvents();

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(event.getId());
            assertThat(result.get(0).getTitle()).isEqualTo("Campus Notice");
            assertThat(result.get(0).getAuthorName()).isEqualTo("Super Admin");
            assertThat(result.get(0).getDormitoryId()).isNull();
        }
    }

    @Nested
    @DisplayName("createCampusEvent")
    class CreateCampusEventTests {

        @Test
        @DisplayName("Throws BusinessRuleException when category is not a campus category")
        void throwsExceptionWhenCategoryInvalid() {
            // Arrange
            User author = User.builder().id(UUID.randomUUID()).build();
            CreateCampusEventRequestDto request = CreateCampusEventRequestDto.builder()
                    .title("Event")
                    .description("Desc")
                    .category(DormEventCategory.STUDENT_EVENT)
                    .priority(DormEventPriority.INFO)
                    .eventDate(Instant.now())
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.createCampusEvent(author, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Campus events must use BED_LINEN, TECHNICAL_OUTAGE, or ADMIN_NOTICE");
            verify(dormEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws BusinessRuleException when endDate is before eventDate")
        void throwsExceptionWhenEndDateBeforeEventDate() {
            // Arrange
            User author = User.builder().id(UUID.randomUUID()).build();
            Instant now = Instant.now();
            CreateCampusEventRequestDto request = CreateCampusEventRequestDto.builder()
                    .title("Event")
                    .description("Desc")
                    .category(DormEventCategory.TECHNICAL_OUTAGE)
                    .priority(DormEventPriority.WARNING)
                    .eventDate(now)
                    .endDate(now.minusSeconds(60))
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.createCampusEvent(author, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Event end date must be on or after event date");
            verify(dormEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Creates campus event with pinned = true and dormitory = null")
        void createsCampusEventSuccessfully() {
            // Arrange
            User author = User.builder().id(UUID.randomUUID()).email("super@pk.edu.pl").firstName("Super").lastName("Admin").build();
            Instant now = Instant.now();
            Instant end = now.plusSeconds(3600);
            CreateCampusEventRequestDto request = CreateCampusEventRequestDto.builder()
                    .title(" Outage notice ")
                    .description(" Water outage ")
                    .category(DormEventCategory.TECHNICAL_OUTAGE)
                    .priority(DormEventPriority.CRITICAL)
                    .pinned(false) // should be forced to true
                    .eventDate(now)
                    .endDate(end)
                    .build();

            UUID eventId = UUID.randomUUID();
            when(dormEventRepository.save(any(DormEvent.class))).thenAnswer(invocation -> {
                DormEvent e = invocation.getArgument(0);
                e.setId(eventId);
                return e;
            });

            // Act
            DormEventDto result = superAdminService.createCampusEvent(author, request);

            // Assert
            ArgumentCaptor<DormEvent> captor = ArgumentCaptor.forClass(DormEvent.class);
            verify(dormEventRepository).save(captor.capture());
            DormEvent saved = captor.getValue();

            assertThat(saved.getTitle()).isEqualTo("Outage notice");
            assertThat(saved.getDescription()).isEqualTo("Water outage");
            assertThat(saved.getCategory()).isEqualTo(DormEventCategory.TECHNICAL_OUTAGE);
            assertThat(saved.getPriority()).isEqualTo(DormEventPriority.CRITICAL);
            assertThat(saved.isPinned()).isTrue();
            assertThat(saved.getDormitory()).isNull();
            assertThat(saved.getAuthor()).isEqualTo(author);

            assertThat(result.getId()).isEqualTo(eventId);
            assertThat(result.getTitle()).isEqualTo("Outage notice");
            assertThat(result.isPinned()).isTrue();
            assertThat(result.getAuthorName()).isEqualTo("Super Admin");
        }
    }

    @Nested
    @DisplayName("updateCampusEvent")
    class UpdateCampusEventTests {

        @Test
        @DisplayName("Throws ResourceNotFoundException when event not found")
        void throwsExceptionWhenNotFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder().build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateCampusEvent(eventId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event belongs to a specific dormitory")
        void throwsExceptionWhenNotCampusWide() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Dormitory dorm = Dormitory.builder().id(UUID.randomUUID()).build();
            DormEvent event = DormEvent.builder().id(eventId).dormitory(dorm).build();
            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder().build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateCampusEvent(eventId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only campus-wide events can be managed here");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when updated category is not campus-compatible")
        void throwsExceptionWhenCategoryNotCampusCompatible() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            DormEvent event = DormEvent.builder().id(eventId).dormitory(null).build();
            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder()
                    .category(DormEventCategory.STUDENT_EVENT)
                    .build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateCampusEvent(eventId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Campus events must use BED_LINEN, TECHNICAL_OUTAGE, or ADMIN_NOTICE");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when updated dates violate ordering")
        void throwsExceptionWhenDatesInvalid() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            DormEvent event = DormEvent.builder()
                    .id(eventId)
                    .dormitory(null)
                    .eventDate(now)
                    .endDate(now.plusSeconds(3600))
                    .build();
            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder()
                    .endDate(now.minusSeconds(100))
                    .build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.updateCampusEvent(eventId, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Event end date must be on or after event date");
        }

        @Test
        @DisplayName("Updates fields and keeps pinned = true")
        void updatesCampusEventSuccessfully() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant end = now.plusSeconds(3600);
            DormEvent event = DormEvent.builder()
                    .id(eventId)
                    .dormitory(null)
                    .title("Old Title")
                    .description("Old Desc")
                    .category(DormEventCategory.ADMIN_NOTICE)
                    .priority(DormEventPriority.INFO)
                    .pinned(true)
                    .eventDate(now)
                    .endDate(end)
                    .build();

            UpdateCampusEventRequestDto request = UpdateCampusEventRequestDto.builder()
                    .title(" New Title ")
                    .description(" New Desc ")
                    .category(DormEventCategory.BED_LINEN)
                    .priority(DormEventPriority.WARNING)
                    .pinned(false) // should remain true
                    .build();

            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(dormEventRepository.save(any(DormEvent.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            DormEventDto result = superAdminService.updateCampusEvent(eventId, request);

            // Assert
            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getDescription()).isEqualTo("New Desc");
            assertThat(result.getCategory()).isEqualTo(DormEventCategory.BED_LINEN);
            assertThat(result.getPriority()).isEqualTo(DormEventPriority.WARNING);
            assertThat(result.isPinned()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteCampusEvent")
    class DeleteCampusEventTests {

        @Test
        @DisplayName("Throws ResourceNotFoundException when event not found")
        void throwsExceptionWhenNotFound() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.deleteCampusEvent(eventId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Event not found");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event belongs to a dormitory")
        void throwsExceptionWhenNotCampusWide() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Dormitory dorm = Dormitory.builder().id(UUID.randomUUID()).build();
            DormEvent event = DormEvent.builder().id(eventId).dormitory(dorm).build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Act & Assert
            assertThatThrownBy(() -> superAdminService.deleteCampusEvent(eventId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only campus-wide events can be managed here");
            verify(dormEventRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Deletes campus-wide event successfully")
        void deletesCampusEventSuccessfully() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            DormEvent event = DormEvent.builder().id(eventId).dormitory(null).build();
            when(dormEventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Act
            superAdminService.deleteCampusEvent(eventId);

            // Assert
            verify(dormEventRepository).delete(event);
        }
    }
}

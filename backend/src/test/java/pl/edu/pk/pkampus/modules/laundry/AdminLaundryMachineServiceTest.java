package pl.edu.pk.pkampus.modules.laundry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.laundry.dto.AdminLaundryMachineDto;
import pl.edu.pk.pkampus.modules.laundry.dto.CreateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.laundry.dto.UpdateLaundryMachineRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLaundryMachineService POJO/Mockito unit tests (AAA)")
class AdminLaundryMachineServiceTest {

    @Mock
    private LaundryMachineRepository laundryMachineRepository;

    @InjectMocks
    private AdminLaundryMachineService service;

    private Dormitory dorm;
    private User dormAdmin;
    private User resident;
    private LaundryMachine machine;

    @BeforeEach
    void setUp() {
        dorm = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS Pralnia")
                .build();

        dormAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@pk.edu.pl")
                .role(UserRole.DORM_ADMIN)
                .dormitory(dorm)
                .build();

        resident = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .role(UserRole.RESIDENT)
                .dormitory(dorm)
                .build();

        machine = LaundryMachine.builder()
                .id(UUID.randomUUID())
                .dormitory(dorm)
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .status(LaundryMachineStatus.AVAILABLE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("listForAdmin throws AccessDeniedException when caller is not DORM_ADMIN")
    void listForAdminThrowsWhenNotDormAdmin() {
        // Arrange & Act & Assert
        assertThrows(AccessDeniedException.class, () -> service.listForAdmin(resident));
    }

    @Test
    @DisplayName("listForAdmin throws BusinessRuleException when admin has no dormitory assigned")
    void listForAdminThrowsWhenNoDormitory() {
        // Arrange
        dormAdmin.setDormitory(null);

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.listForAdmin(dormAdmin));
        assertTrue(ex.getMessage().contains("no dormitory assigned"));
    }

    @Test
    @DisplayName("listForAdmin returns machines for admin's dormitory")
    void listForAdminSuccess() {
        // Arrange
        when(laundryMachineRepository.findAllByDormitoryIdOrderByMachineIdentifierAsc(dorm.getId()))
                .thenReturn(List.of(machine));

        // Act
        List<AdminLaundryMachineDto> dtos = service.listForAdmin(dormAdmin);

        // Assert
        assertEquals(1, dtos.size());
        assertEquals(machine.getId(), dtos.get(0).getId());
        assertEquals("Pralka 1", dtos.get(0).getMachineIdentifier());
        assertEquals("Parter", dtos.get(0).getFloorLocation());
        assertEquals(dorm.getId(), dtos.get(0).getDormitoryId());
    }

    @Test
    @DisplayName("create throws BusinessRuleException when machine identifier already exists")
    void createThrowsWhenIdentifierAlreadyExists() {
        // Arrange
        CreateLaundryMachineRequestDto request = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("Pralka 1")
                .floorLocation("Parter")
                .build();

        when(laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCase(dorm.getId(), "Pralka 1"))
                .thenReturn(true);

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.create(dormAdmin, request));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("create successfully creates machine and trims strings")
    void createSuccess() {
        // Arrange
        CreateLaundryMachineRequestDto request = CreateLaundryMachineRequestDto.builder()
                .machineIdentifier("  Pralka 2  ")
                .floorLocation("  I piętro  ")
                .build();

        when(laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCase(dorm.getId(), "Pralka 2"))
                .thenReturn(false);
        when(laundryMachineRepository.save(any(LaundryMachine.class))).thenAnswer(invocation -> {
            LaundryMachine m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        // Act
        AdminLaundryMachineDto result = service.create(dormAdmin, request);

        // Assert
        assertNotNull(result);
        assertEquals("Pralka 2", result.getMachineIdentifier());
        assertEquals("I piętro", result.getFloorLocation());
        assertEquals(LaundryMachineStatus.AVAILABLE, result.getStatus());
        assertEquals(dorm.getId(), result.getDormitoryId());
    }

    @Test
    @DisplayName("update throws ResourceNotFoundException when machine does not exist in admin's dorm")
    void updateThrowsWhenNotFound() {
        // Arrange
        UUID machineId = UUID.randomUUID();
        UpdateLaundryMachineRequestDto request = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("Nowa nazwa")
                .build();

        when(laundryMachineRepository.findByIdAndDormitoryId(machineId, dorm.getId()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(dormAdmin, machineId, request));
    }

    @Test
    @DisplayName("update throws BusinessRuleException when new identifier already exists for another machine")
    void updateThrowsWhenIdentifierDuplicate() {
        // Arrange
        UpdateLaundryMachineRequestDto request = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("Pralka 3")
                .build();

        when(laundryMachineRepository.findByIdAndDormitoryId(machine.getId(), dorm.getId()))
                .thenReturn(Optional.of(machine));
        when(laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCaseAndIdNot(
                dorm.getId(), "Pralka 3", machine.getId()))
                .thenReturn(true);

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.update(dormAdmin, machine.getId(), request));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("update successfully updates machine fields")
    void updateSuccess() {
        // Arrange
        UpdateLaundryMachineRequestDto request = UpdateLaundryMachineRequestDto.builder()
                .machineIdentifier("  Pralka Zaktualizowana  ")
                .floorLocation("  Piwnica  ")
                .status(LaundryMachineStatus.OUT_OF_ORDER)
                .build();

        when(laundryMachineRepository.findByIdAndDormitoryId(machine.getId(), dorm.getId()))
                .thenReturn(Optional.of(machine));
        when(laundryMachineRepository.existsByDormitoryIdAndMachineIdentifierIgnoreCaseAndIdNot(
                dorm.getId(), "Pralka Zaktualizowana", machine.getId()))
                .thenReturn(false);
        when(laundryMachineRepository.save(machine)).thenReturn(machine);

        // Act
        AdminLaundryMachineDto result = service.update(dormAdmin, machine.getId(), request);

        // Assert
        assertNotNull(result);
        assertEquals("Pralka Zaktualizowana", machine.getMachineIdentifier());
        assertEquals("Piwnica", machine.getFloorLocation());
        assertEquals(LaundryMachineStatus.OUT_OF_ORDER, machine.getStatus());
        verify(laundryMachineRepository).save(machine);
    }
}

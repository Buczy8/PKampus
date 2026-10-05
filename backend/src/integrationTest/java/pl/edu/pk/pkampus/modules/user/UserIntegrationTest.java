package pl.edu.pk.pkampus.modules.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("User API Integration Tests")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private User resident;
    private Room room;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Integracja")
                .code("DI-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 10")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(120)
                .build());

        room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("105")
                .capacity(2)
                .floor(1)
                .build());

        resident = userRepository.save(User.builder()
                .email("res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Kamil")
                .lastName("Kowalski")
                .phoneNumber("+48500600700")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("999")
                .build());

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusDays(5))
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns authenticated resident profile with assigned room")
    void getMeReturnsAuthenticatedProfile() throws Exception {
        String token = "Bearer " + jwtService.generateToken(resident);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(resident.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(resident.getEmail()))
                .andExpect(jsonPath("$.data.firstName").value("Kamil"))
                .andExpect(jsonPath("$.data.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.data.roomNumber").value("105"))
                .andExpect(jsonPath("$.data.dormitoryName").value(dorm.getName()));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} returns 404 because arbitrary profile lookup was removed")
    void getUserByIdIsNotExposed() throws Exception {
        String token = "Bearer " + jwtService.generateToken(resident);
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}", randomId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}

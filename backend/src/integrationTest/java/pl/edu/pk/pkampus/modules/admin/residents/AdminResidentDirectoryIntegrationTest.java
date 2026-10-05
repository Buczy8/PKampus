package pl.edu.pk.pkampus.modules.admin.residents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ADS resident directory API integration tests")
class AdminResidentDirectoryIntegrationTest {

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

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User dormAdmin1;
    private User resident1;
    private User residentOther;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Dir 1")
                .code("D1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Dir 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Dir 2")
                .code("D2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Dir 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        Room room = roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());

        dormAdmin1 = saveUser("ads-dir-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null, UserStatus.ACTIVE);
        resident1 = saveUser("res-dir-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101", UserStatus.ACTIVE);
        residentOther = saveUser("res-oth-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm2, "201", UserStatus.ACTIVE);

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident1)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now())
                .build());
    }

    @Test
    @DisplayName("DORM_ADMIN lists only own dormitory ACTIVE/BLOCKED residents")
    void listScoped() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value(resident1.getEmail()));
    }

    @Test
    @DisplayName("DORM_ADMIN can block and unblock resident")
    void blockAndUnblock() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/block")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/unblock")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("DORM_ADMIN can issue and revoke ROOM_BAN")
    void roomBan() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/room-ban")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "durationMonths": 2,
                                  "reason": "Dewastacja wyposażenia salki"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sanctionType").value("ROOM_BAN"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sanctionId = body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/room-ban/" + sanctionId + "/revoke")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("Cannot act on resident from another dormitory")
    void foreignResidentNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentOther.getId() + "/block")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Checkout removes resident from directory list")
    void checkout() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/checkout")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_OUT"));

        mockMvc.perform(get("/api/v1/admin/residents")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Cannot block resident who is already blocked")
    void cannotBlockAlreadyBlocked() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/block")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/block")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Cannot unblock resident who is not blocked")
    void cannotUnblockActiveResident() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/unblock")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Cannot issue duplicate active ROOM_BAN")
    void duplicateRoomBanFails() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/room-ban")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "durationMonths": 1,
                                  "reason": "First ban"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/room-ban")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "durationMonths": 2,
                                  "reason": "Second ban"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Revoking non-existent sanction returns 404")
    void revokeNonExistentSanctionReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + resident1.getId() + "/room-ban/" + UUID.randomUUID() + "/revoke")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isNotFound());
    }

    private User saveUser(String email, UserRole role, Dormitory dorm, String room, UserStatus status) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+48111111111")
                .role(role)
                .status(status)
                .dormitory(dorm)
                .declaredRoomNumber(room)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}

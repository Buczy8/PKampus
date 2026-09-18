package pl.edu.pk.pkampus.modules.rooms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Thematic rooms API integration tests")
class ThematicRoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MinioStorageService minioStorageService;

    @MockBean
    private EmailService emailService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User dormAdmin1;
    private User resident1;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Rooms 1")
                .code("R1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Test 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Rooms 2")
                .code("R2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Test 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dormAdmin1 = saveUser("ads1-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null);
        resident1 = saveUser("res-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101");
    }

    @Test
    @DisplayName("DORM_ADMIN can create and list thematic rooms in own dormitory")
    void adminCreatesAndListsRooms() throws Exception {
        mockMvc.perform(post("/api/v1/admin/thematic-rooms")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Salka Kujon",
                                  "maxCapacity": 16,
                                  "openingTime": "06:00:00",
                                  "closingTime": "23:30:00",
                                  "maxDurationHours": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Salka Kujon"))
                .andExpect(jsonPath("$.data.maxCapacity").value(16))
                .andExpect(jsonPath("$.data.maxDurationHours").value(4))
                .andExpect(jsonPath("$.data.spansMidnight").value(false));

        mockMvc.perform(get("/api/v1/admin/thematic-rooms")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Overnight hours auto-set spansMidnight")
    void overnightHours() throws Exception {
        mockMvc.perform(post("/api/v1/admin/thematic-rooms")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Chillout",
                                  "maxCapacity": 30,
                                  "openingTime": "14:00:00",
                                  "closingTime": "02:00:00",
                                  "maxDurationHours": 12
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.spansMidnight").value(true))
                .andExpect(jsonPath("$.data.maxDurationHours").value(12));
    }

    @Test
    @DisplayName("Cannot update thematic room from another dormitory")
    void cannotUpdateForeignRoom() throws Exception {
        ThematicRoom foreign = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm2)
                .name("Obca salka")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        mockMvc.perform(patch("/api/v1/admin/thematic-rooms/" + foreign.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RESIDENT is forbidden on admin thematic-rooms API")
    void residentForbiddenOnAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/thematic-rooms")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESIDENT catalog shows AVAILABLE rooms from own dormitory")
    void residentCatalog() throws Exception {
        thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm1)
                .name("Dostępna")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm1)
                .name("Remont")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.MAINTENANCE)
                .build());

        thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm2)
                .name("Inny DS")
                .maxCapacity(10)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 30))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        mockMvc.perform(get("/api/v1/rooms")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Dostępna"));
    }

    private User saveUser(String email, UserRole role, Dormitory dorm, String room) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+48111111111")
                .role(role)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber(room)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }
}

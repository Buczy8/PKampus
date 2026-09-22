package pl.edu.pk.pkampus.modules.admin;

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
@DisplayName("ADS receptionist accounts API integration tests")
class AdminReceptionistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

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

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Portier 1")
                .code("P1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Portier 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Portier 2")
                .code("P2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Portier 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dormAdmin1 = saveUser("ads-port-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null);
        resident1 = saveUser("res-port-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101");
    }

    @Test
    @DisplayName("DORM_ADMIN can create and list receptionists in own dormitory")
    void adminCreatesAndLists() throws Exception {
        String email = "portier-" + UUID.randomUUID() + "@pk.edu.pl";

        mockMvc.perform(post("/api/v1/admin/receptionists")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jan",
                                  "lastName": "Portier",
                                  "email": "%s",
                                  "phoneNumber": "+48123456789",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("MUST_CHANGE_PASSWORD"))
                .andExpect(jsonPath("$.data.dormitoryId").value(dorm1.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/receptionists")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Cannot update receptionist from another dormitory")
    void cannotUpdateForeignReceptionist() throws Exception {
        User foreign = saveUser(
                "foreign-port-" + UUID.randomUUID() + "@pk.edu.pl",
                UserRole.RECEPTIONIST,
                dorm2,
                null
        );

        mockMvc.perform(patch("/api/v1/admin/receptionists/" + foreign.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DORM_ADMIN can block receptionist")
    void adminBlocksReceptionist() throws Exception {
        User porter = saveUser(
                "block-port-" + UUID.randomUUID() + "@pk.edu.pl",
                UserRole.RECEPTIONIST,
                dorm1,
                null
        );

        mockMvc.perform(patch("/api/v1/admin/receptionists/" + porter.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));
    }

    @Test
    @DisplayName("RESIDENT is forbidden on admin receptionists API")
    void residentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/receptionists")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isForbidden());
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

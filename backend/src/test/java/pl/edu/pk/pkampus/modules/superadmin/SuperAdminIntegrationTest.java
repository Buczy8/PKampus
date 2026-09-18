package pl.edu.pk.pkampus.modules.superadmin;

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

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Super Admin API integration tests")
class SuperAdminIntegrationTest {

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

    @MockBean
    private MinioStorageService minioStorageService;

    @MockBean
    private EmailService emailService;

    private Dormitory dorm;
    private User superAdmin;
    private User dormAdmin;
    private User resident;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Super")
                .code("SU-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Super 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        superAdmin = userRepository.save(User.builder()
                .email("super-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Super")
                .lastName("Admin")
                .phoneNumber("+48111111111")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        dormAdmin = userRepository.save(User.builder()
                .email("ads-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Dorm")
                .lastName("Admin")
                .phoneNumber("+48222222222")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());

        resident = userRepository.save(User.builder()
                .email("res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Res")
                .lastName("Ident")
                .phoneNumber("+48333333333")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("101")
                .build());
    }

    @Test
    @DisplayName("SUPER_ADMIN can create and list dormitories")
    void superAdminCreatesDormitory() throws Exception {
        String code = "N" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        mockMvc.perform(post("/api/v1/superadmin/dormitories")
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "DS Nowy",
                                  "address": "ul. Nowa 1",
                                  "floorsCount": 5
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.laundrySlotDurationMinutes").value(180));

        mockMvc.perform(get("/api/v1/superadmin/dormitories")
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("DORM_ADMIN receives 403 on superadmin dormitories")
    void dormAdminForbiddenOnDormitories() throws Exception {
        mockMvc.perform(get("/api/v1/superadmin/dormitories")
                        .header("Authorization", bearer(dormAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN can create and block dormitory admin")
    void superAdminManagesDormAdmins() throws Exception {
        String email = "newads-" + UUID.randomUUID() + "@pk.edu.pl";
        String body = mockMvc.perform(post("/api/v1/superadmin/dorm-admins")
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Anna",
                                  "lastName": "Kierownik",
                                  "email": "%s",
                                  "phoneNumber": "+48444444444",
                                  "password": "Password123!",
                                  "dormitoryId": "%s"
                                }
                                """.formatted(email, dorm.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(patch("/api/v1/superadmin/dorm-admins/" + id)
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/superadmin/dorm-admins")
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("SUPER_ADMIN can publish campus event and resident sees banner")
    void campusEventAppearsOnBanner() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(2, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/superadmin/events")
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Awaria wody",
                                  "description": "Brak ciepłej wody",
                                  "category": "TECHNICAL_OUTAGE",
                                  "priority": "CRITICAL",
                                  "pinned": true,
                                  "eventDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.priority").value("CRITICAL"));

        mockMvc.perform(get("/api/v1/events/banner")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Awaria wody"));
    }

    @Test
    @DisplayName("Resident cannot create campus events")
    void residentForbiddenOnCampusEvents() throws Exception {
        mockMvc.perform(post("/api/v1/superadmin/events")
                        .header("Authorization", bearer(resident))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Nope",
                                  "description": "Nope",
                                  "category": "ADMIN_NOTICE",
                                  "priority": "INFO",
                                  "eventDate": "%s"
                                }
                                """.formatted(Instant.now())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN can delete campus event")
    void superAdminDeletesCampusEvent() throws Exception {
        String body = mockMvc.perform(post("/api/v1/superadmin/events")
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Tymczasowe",
                                  "description": "Do usunięcia",
                                  "category": "ADMIN_NOTICE",
                                  "priority": "INFO",
                                  "pinned": false,
                                  "eventDate": "%s"
                                }
                                """.formatted(Instant.now())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = body.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/v1/superadmin/events/" + id)
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk());
    }

    private String bearer(User user) {
        String room = user.getDeclaredRoomNumber() != null ? user.getDeclaredRoomNumber() : null;
        return "Bearer " + jwtService.generateToken(user, room);
    }
}

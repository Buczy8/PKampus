package pl.edu.pk.pkampus.modules.events;

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

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
@DisplayName("ADS dorm events API integration tests")
class AdminDormEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private DormEventRepository dormEventRepository;

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
    private User superAdmin;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Events 1")
                .code("E1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Event 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Events 2")
                .code("E2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Event 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dormAdmin1 = saveUser("ads-ev-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null);
        resident1 = saveUser("res-ev-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101");
        superAdmin = saveUser("sa-ev-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.SUPER_ADMIN, null, null);
    }

    @Test
    @DisplayName("DORM_ADMIN can create and list notices for own dormitory")
    void adminCreatesAndLists() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/admin/events")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Wymiana pościeli",
                                  "description": "W piątek od 10:00",
                                  "priority": "INFO",
                                  "eventDate": "%s"
                                }
                                """.formatted(start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Wymiana pościeli"))
                .andExpect(jsonPath("$.data.category").value("ADMIN_NOTICE"))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.dormitoryId").value(dorm1.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/events")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Cannot update or delete campus or foreign dorm notice")
    void cannotTouchForeignOrCampus() throws Exception {
        DormEvent campus = dormEventRepository.save(DormEvent.builder()
                .author(superAdmin)
                .dormitory(null)
                .title("Campus")
                .description("AOS")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.INFO)
                .pinned(true)
                .eventDate(Instant.now())
                .build());

        DormEvent foreign = dormEventRepository.save(DormEvent.builder()
                .author(superAdmin)
                .dormitory(dorm2)
                .title("Obcy DS")
                .description("Nie twój")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.INFO)
                .pinned(true)
                .eventDate(Instant.now())
                .build());

        mockMvc.perform(patch("/api/v1/admin/events/" + campus.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hack\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/admin/events/" + foreign.getId())
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RESIDENT is forbidden on admin events API")
    void residentForbiddenOnAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Resident feed shows own dorm and campus notices, not foreign dorm")
    void residentFeedScoped() throws Exception {
        dormEventRepository.save(DormEvent.builder()
                .author(dormAdmin1)
                .dormitory(dorm1)
                .title("Nasz DS")
                .description("Widoczny")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.INFO)
                .pinned(true)
                .eventDate(Instant.now())
                .build());

        dormEventRepository.save(DormEvent.builder()
                .author(superAdmin)
                .dormitory(null)
                .title("Kampus")
                .description("Widoczny")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.WARNING)
                .pinned(true)
                .eventDate(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build());

        dormEventRepository.save(DormEvent.builder()
                .author(superAdmin)
                .dormitory(dorm2)
                .title("Obcy")
                .description("Ukryty")
                .category(DormEventCategory.ADMIN_NOTICE)
                .priority(DormEventPriority.INFO)
                .pinned(true)
                .eventDate(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/events")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.title=='Nasz DS')]").exists())
                .andExpect(jsonPath("$.data[?(@.title=='Kampus')]").exists())
                .andExpect(jsonPath("$.data[?(@.title=='Obcy')]").doesNotExist());
    }

    @Test
    @DisplayName("CRITICAL dorm notice appears on resident banner")
    void criticalDormNoticeOnBanner() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/admin/events")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Awaria windy",
                                  "description": "Winda nie działa",
                                  "priority": "CRITICAL",
                                  "eventDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(start, end)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/events/banner")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Awaria windy"));
    }

    @Test
    @DisplayName("Create notice with endDate before eventDate returns 422 Unprocessable Entity")
    void createWithInvalidDatesReturns422() throws Exception {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.minus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/admin/events")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Błędne daty",
                                  "description": "Opis",
                                  "priority": "INFO",
                                  "eventDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(start, end)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Create notice with blank title returns 400 Bad Request")
    void createWithBlankTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/events")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "Opis",
                                  "priority": "INFO",
                                  "eventDate": "%s"
                                }
                                """.formatted(Instant.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Banner returns empty/null data when no active critical notice exists")
    void bannerReturnsNullWhenNoCriticalNotice() throws Exception {
        mockMvc.perform(get("/api/v1/events/banner")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Unauthenticated request to admin events API returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events"))
                .andExpect(status().isUnauthorized());
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
        return "Bearer " + jwtService.generateToken(user);
    }
}

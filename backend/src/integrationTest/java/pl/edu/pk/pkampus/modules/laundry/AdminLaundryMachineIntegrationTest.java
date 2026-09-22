package pl.edu.pk.pkampus.modules.laundry;

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
@DisplayName("ADS laundry machines API integration tests")
class AdminLaundryMachineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private LaundryMachineRepository laundryMachineRepository;

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
                .name("DS Laundry Admin 1")
                .code("LA-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Laundry 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Laundry Admin 2")
                .code("LB-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Laundry 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dormAdmin1 = saveUser("ads-lm-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null);
        resident1 = saveUser("res-lm-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101");
    }

    @Test
    @DisplayName("DORM_ADMIN can create and list laundry machines in own dormitory")
    void adminCreatesAndLists() throws Exception {
        mockMvc.perform(post("/api/v1/admin/laundry-machines")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "machineIdentifier": "P1",
                                  "floorLocation": "Piwnica"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.machineIdentifier").value("P1"))
                .andExpect(jsonPath("$.data.floorLocation").value("Piwnica"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.dormitoryId").value(dorm1.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/laundry-machines")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Duplicate machine identifier in same dormitory is rejected")
    void duplicateIdentifierRejected() throws Exception {
        laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm1)
                .machineIdentifier("P1")
                .floorLocation("1")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        mockMvc.perform(post("/api/v1/admin/laundry-machines")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "machineIdentifier": "p1",
                                  "floorLocation": "2"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Cannot update laundry machine from another dormitory")
    void cannotUpdateForeignMachine() throws Exception {
        LaundryMachine foreign = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm2)
                .machineIdentifier("X1")
                .floorLocation("Piwnica")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        mockMvc.perform(patch("/api/v1/admin/laundry-machines/" + foreign.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floorLocation\":\"Hack\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RESIDENT is forbidden on admin laundry-machines API")
    void residentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/laundry-machines")
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

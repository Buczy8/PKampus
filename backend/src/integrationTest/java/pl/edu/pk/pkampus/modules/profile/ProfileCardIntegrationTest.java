package pl.edu.pk.pkampus.modules.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
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
@DisplayName("Resident card API integration tests")
class ProfileCardIntegrationTest {

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

    @Autowired
    private CardVerificationService cardVerificationService;

    @Autowired
    private AuthenticatedUserCache authenticatedUserCache;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private User resident;
    private User receptionist;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Card Test")
                .code("CT-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Kartowa 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        resident = userRepository.save(User.builder()
                .email("card.resident+" + UUID.randomUUID() + "@test.pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Anna")
                .lastName("Kowalska")
                .phoneNumber("+48111222333")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("312")
                .build());

        receptionist = userRepository.save(User.builder()
                .email("card.porter+" + UUID.randomUUID() + "@test.pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Piotr")
                .lastName("Portier")
                .phoneNumber("+48444555666")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());
    }

    @Test
    @DisplayName("ACTIVE resident receives card with day code matching porter strip")
    void activeResidentGetsCard() throws Exception {
        String token = jwtService.generateToken(resident);
        CardDayToken expected = cardVerificationService.todaysToken();

        mockMvc.perform(get("/api/v1/profile/card")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Anna"))
                .andExpect(jsonPath("$.data.lastName").value("Kowalska"))
                .andExpect(jsonPath("$.data.dormitoryName").value("DS Card Test"))
                .andExpect(jsonPath("$.data.roomNumber").value("312"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.dayCode").value(expected.dayCode()))
                .andExpect(jsonPath("$.data.dayColorHex").value(expected.dayColorHex()))
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty());

        mockMvc.perform(get("/api/v1/receptionist/card-day")
                        .header("Authorization", "Bearer " + jwtService.generateToken(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayCode").value(expected.dayCode()))
                .andExpect(jsonPath("$.data.dayColorHex").value(expected.dayColorHex()));
    }

    @Test
    @DisplayName("BLOCKED resident gets 403 ACCOUNT_BLOCKED")
    void blockedResidentForbidden() throws Exception {
        // Issue JWT while still ACTIVE (login would reject BLOCKED), then flip status.
        String token = jwtService.generateToken(resident);
        resident.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(resident);
        authenticatedUserCache.invalidate(resident.getId());

        mockMvc.perform(get("/api/v1/profile/card")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ACCOUNT_BLOCKED"));
    }

    @Test
    @DisplayName("CHECKED_OUT resident gets 403 ACCOUNT_CHECKED_OUT")
    void checkedOutResidentForbidden() throws Exception {
        String token = jwtService.generateToken(resident);
        resident.setStatus(UserStatus.CHECKED_OUT);
        userRepository.saveAndFlush(resident);
        authenticatedUserCache.invalidate(resident.getId());

        mockMvc.perform(get("/api/v1/profile/card")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ACCOUNT_CHECKED_OUT"));
    }

    @Test
    @DisplayName("Non-resident (e.g. RECEPTIONIST) gets 403 Forbidden")
    void receptionistForbidden() throws Exception {
        String token = jwtService.generateToken(receptionist);

        mockMvc.perform(get("/api/v1/profile/card")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/profile/card"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Resident with active room assignment returns assigned room number instead of declared")
    void activeRoomAssignmentOverridesDeclaredRoom() throws Exception {
        Room room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("505")
                .floor(5)
                .capacity(2)
                .build());

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear("2026/2027")
                .isActive(true)
                .checkInDate(LocalDate.now())
                .build());

        String token = jwtService.generateToken(resident);

        mockMvc.perform(get("/api/v1/profile/card")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomNumber").value("505"));
    }
}

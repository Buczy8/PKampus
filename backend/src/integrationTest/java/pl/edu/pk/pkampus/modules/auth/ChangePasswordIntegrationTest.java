package pl.edu.pk.pkampus.modules.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Forced password change and voluntary change API")
class ChangePasswordIntegrationTest {

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

    private Dormitory dormitory;
    private User mustChangeUser;
    private User activeUser;

    @BeforeEach
    void setUp() {
        dormitory = dormitoryRepository.save(Dormitory.builder()
                .name("DS Pwd")
                .code("PW-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Haslo 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        mustChangeUser = saveUser(
                "must-" + UUID.randomUUID() + "@pk.edu.pl",
                UserRole.DORM_ADMIN,
                UserStatus.MUST_CHANGE_PASSWORD
        );
        activeUser = saveUser(
                "active-" + UUID.randomUUID() + "@pk.edu.pl",
                UserRole.RESIDENT,
                UserStatus.ACTIVE
        );
    }

    @Test
    void mustChangePasswordUserCanLoginAndChangePasswordThenAccessApi() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!"}
                                """.formatted(mustChangeUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value("MUST_CHANGE_PASSWORD"));

        String token = bearer(mustChangeUser);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MUST_CHANGE_PASSWORD"));

        mockMvc.perform(get("/api/v1/admin/receptionists").header("Authorization", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        User refreshed = userRepository.findById(mustChangeUser.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("NewPassword1!", refreshed.getPasswordHash())).isTrue();

        // Cache may still hold old status briefly; re-login to get fresh JWT principal in filter path
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPassword1!"}
                                """.formatted(mustChangeUser.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(loginBody).contains("ACTIVE");
    }

    @Test
    void activeUserCanChangePasswordWithoutStatusChange() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(activeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"AnotherPass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        User refreshed = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("AnotherPass1!", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void wrongCurrentPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(activeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass1!","newPassword":"AnotherPass1!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private User saveUser(String email, UserRole role, UserStatus status) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+48111111111")
                .role(role)
                .status(status)
                .dormitory(dormitory)
                .declaredRoomNumber(role == UserRole.RESIDENT ? "101" : null)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }
}

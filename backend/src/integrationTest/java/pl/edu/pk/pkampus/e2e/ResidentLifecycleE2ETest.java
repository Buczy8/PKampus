package pl.edu.pk.pkampus.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.admin.dto.ActivateResidentRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E: Resident Lifecycle (Registration -> Verification -> Approval -> Login -> Profile -> Password Change)")
class ResidentLifecycleE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SignedEmailTokenService signedEmailTokenService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private User dormAdmin;
    private Room room;

    @BeforeEach
    void setUp() {
        when(minioStorageService.uploadAvatar(any())).thenReturn("avatar.jpg");

        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Bydgoska")
                .code("DSB-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Bydgoska 19")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(120)
                .build());

        room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("214")
                .floor(2)
                .capacity(2)
                .build());

        dormAdmin = userRepository.save(User.builder()
                .email("ads-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Kierownik")
                .lastName("Osiedla")
                .phoneNumber("+48123456789")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());
    }

    @Test
    @DisplayName("Complete Resident Journey: Register -> Verify Email -> ADS Approve -> Login -> View Profile -> Change Password")
    void completeResidentJourney() throws Exception {
        String residentEmail = "student-" + UUID.randomUUID() + "@pk.edu.pl";
        String initialPassword = "Password123!";
        String newPassword = "NewSecretPassword456!";

        // -------------------------------------------------------------
        // KROK 1: Rejestracja mieszkańca z załącznikiem zdjęcia twarzy (UC-AUTH-01)
        // -------------------------------------------------------------
        RegisterRequestDto registerDto = RegisterRequestDto.builder()
                .firstName("Piotr")
                .lastName("Kowalski")
                .email(residentEmail)
                .password(initialPassword)
                .phoneNumber("+48500600700")
                .dormitoryId(dorm.getId())
                .declaredRoomNumber("214")
                .build();

        MockMultipartFile dataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(registerDto)
        );

        // JPEG magic bytes: 0xFF, 0xD8, 0xFF
        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile photoPart = new MockMultipartFile(
                "photo",
                "face.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                validJpeg
        );

        mockMvc.perform(multipart("/api/v1/auth/register")
                        .file(dataPart)
                        .file(photoPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User registeredUser = userRepository.findByEmail(residentEmail)
                .orElseThrow(() -> new AssertionError("User should exist in database"));
        assertThat(registeredUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL);

        // -------------------------------------------------------------
        // KROK 2: Weryfikacja adresu e-mail przez podpisany token HMAC (UC-AUTH-01 etap 2)
        // -------------------------------------------------------------
        String verificationToken = signedEmailTokenService.generateToken(registeredUser.getId(), residentEmail);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        User emailVerifiedUser = userRepository.findByEmail(residentEmail).orElseThrow();
        assertThat(emailVerifiedUser.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);

        // -------------------------------------------------------------
        // KROK 3: Próba logowania przed akceptacją przez administrację powinna być zablokowana
        // -------------------------------------------------------------
        LoginRequestDto loginAttempt = LoginRequestDto.builder()
                .email(residentEmail)
                .password(initialPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAttempt)))
                .andExpect(status().isForbidden());

        // -------------------------------------------------------------
        // KROK 4: Administrator DS przegląda oczekujące wnioski i aktywuje meldunek (UC-ADM-01)
        // -------------------------------------------------------------
        String adminToken = "Bearer " + jwtService.generateToken(dormAdmin, null);

        mockMvc.perform(get("/api/v1/admin/residents/pending")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.email == '%s')]".formatted(residentEmail)).exists());

        ActivateResidentRequestDto activateDto = ActivateResidentRequestDto.builder()
                .roomNumber("214")
                .build();

        mockMvc.perform(post("/api/v1/admin/residents/{id}/activate", registeredUser.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        User activatedUser = userRepository.findByEmail(residentEmail).orElseThrow();
        assertThat(activatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // -------------------------------------------------------------
        // KROK 5: Zalogowanie mieszkańca i odebranie tokenów JWT (UC-AUTH-02)
        // -------------------------------------------------------------
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAttempt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("RESIDENT"))
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("data").path("token").asText();
        String residentBearer = "Bearer " + accessToken;

        // -------------------------------------------------------------
        // KROK 6: Pobranie profilu użytkownika (UC-CARD-01)
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", residentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(residentEmail))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roomNumber").value("214"))
                .andExpect(jsonPath("$.data.dormitoryName").value(dorm.getName()));

        // -------------------------------------------------------------
        // KROK 7: Zmiana hasła przez mieszkańca (UC-AUTH-04)
        // -------------------------------------------------------------
        ChangePasswordRequestDto changePasswordDto = ChangePasswordRequestDto.builder()
                .currentPassword(initialPassword)
                .newPassword(newPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Sprawdzenie, że stare hasło już nie działa
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginAttempt)))
                .andExpect(status().isUnauthorized());

        // Sprawdzenie, że nowe hasło działa poprawnie
        LoginRequestDto newLoginAttempt = LoginRequestDto.builder()
                .email(residentEmail)
                .password(newPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginAttempt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

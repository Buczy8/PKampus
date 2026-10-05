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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@DisplayName("Admin residents API integration tests")
class AdminResidentIntegrationTest {

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
    private ApplicationEvents events;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private User dormAdmin;
    private User residentPending;
    private User residentOtherDorm;
    private User plainResident;
    private User receptionist;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        events.clear();
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Test 1")
                .code("T1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Test 2")
                .code("T2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());
        roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("202")
                .floor(2)
                .capacity(2)
                .build());
        roomRepository.save(Room.builder()
                .dormitory(dorm2)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());

        dormAdmin = userRepository.save(User.builder()
                .email("ads-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Marian")
                .lastName("Kierownik")
                .phoneNumber("+48111111111")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());

        superAdmin = userRepository.save(User.builder()
                .email("super-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Super")
                .lastName("Admin")
                .phoneNumber("+48999999999")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(null)
                .build());

        receptionist = userRepository.save(User.builder()
                .email("portier-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Stan")
                .lastName("Portier")
                .phoneNumber("+48222222222")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());

        plainResident = userRepository.save(User.builder()
                .email("active-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Active")
                .lastName("Student")
                .phoneNumber("+48333333333")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("101")
                .build());

        residentPending = userRepository.save(User.builder()
                .email("pending-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48444444444")
                .avatarUrl("pending-avatar.jpg")
                .role(UserRole.RESIDENT)
                .status(UserStatus.PENDING_APPROVAL)
                .dormitory(dorm1)
                .declaredRoomNumber("101")
                .build());

        residentOtherDorm = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Anna")
                .lastName("Nowak")
                .phoneNumber("+48555555555")
                .role(UserRole.RESIDENT)
                .status(UserStatus.PENDING_APPROVAL)
                .dormitory(dorm2)
                .declaredRoomNumber("101")
                .build());

        when(minioStorageService.getAvatarPresignedUrl(anyString(), anyInt()))
                .thenReturn("https://minio.test/pending-avatar.jpg");
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void residentRoleCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents/pending")
                        .header("Authorization", bearer(plainResident)))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents/pending")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dormAdminListsOnlyOwnDormPendingResidents() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents/pending")
                        .header("Authorization", bearer(dormAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value(residentPending.getEmail()))
                .andExpect(jsonPath("$.data[0].declaredRoomNumber").value("101"))
                .andExpect(jsonPath("$.data[0].avatarUrl").value("https://minio.test/pending-avatar.jpg"));
    }

    @Test
    void dormAdminActivatesResidentAndPersistsAssignment() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roomNumber").value("101"));

        User refreshed = userRepository.findById(residentPending.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(roomAssignmentRepository.findByUserIdAndIsActiveTrue(residentPending.getId())).isPresent();

        verify(emailService, never()).sendAccountActivatedEmail(any(), any(), any(), any());
        ResidentActivatedEvent event = events.stream(ResidentActivatedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ResidentActivatedEvent"));
        assertThat(event.email()).isEqualTo(residentPending.getEmail());
        assertThat(event.firstName()).isEqualTo("Jan");
        assertThat(event.roomNumber()).isEqualTo("101");
        assertThat(event.dormitoryName()).isEqualTo("DS Test 1");
    }

    @Test
    void dormAdminActivatesWithRoomOverride() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomNumber\":\"202\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomNumber").value("202"));

        User refreshed = userRepository.findById(residentPending.getId()).orElseThrow();
        assertThat(refreshed.getDeclaredRoomNumber()).isEqualTo("202");
        assertThat(roomAssignmentRepository.findByUserIdAndIsActiveTrue(residentPending.getId()))
                .get()
                .extracting(ra -> ra.getRoom().getRoomNumber())
                .isEqualTo("202");
    }

    @Test
    void activateUnknownRoomReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomNumber\":\"999\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateForeignDormResidentIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentOtherDorm.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateAlreadyActiveResidentFails() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + plainResident.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateMissingResidentReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + UUID.randomUUID() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectDeletesAccountRemovesAvatarAndEmails() throws Exception {
        UUID id = residentPending.getId();
        String email = residentPending.getEmail();

        mockMvc.perform(post("/api/v1/admin/residents/" + id + "/reject")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not on housing list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findById(id)).isEmpty();
        verify(minioStorageService).removeAvatar("pending-avatar.jpg");
        verify(emailService, never()).sendRegistrationRejectedEmail(any(), any(), any());
        RegistrationRejectedEvent event = events.stream(RegistrationRejectedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected RegistrationRejectedEvent"));
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.firstName()).isEqualTo("Jan");
        assertThat(event.reason()).isEqualTo("Not on housing list");
    }

    @Test
    void rejectWithoutReasonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/reject")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectBlankReasonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/reject")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectForeignDormResidentIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentOtherDorm.getId() + "/reject")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(residentOtherDorm.getId())).isPresent();
    }

    @Test
    void doubleActivateFailsOnSecondCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/residents/" + residentPending.getId() + "/activate")
                        .header("Authorization", bearer(dormAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminListsPendingResidentsAcrossAllDormitories() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents/pending")
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void superAdminCanActivateResidentInAnyDormitory() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/" + residentOtherDorm.getId() + "/activate")
                        .header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roomNumber").value("101"));

        User refreshed = userRepository.findById(residentOtherDorm.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(roomAssignmentRepository.findByUserIdAndIsActiveTrue(residentOtherDorm.getId())).isPresent();
    }
}

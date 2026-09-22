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
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssueLocationType;
import pl.edu.pk.pkampus.modules.issues.IssueRepository;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;
import pl.edu.pk.pkampus.modules.issues.dto.CreateIssueRequestDto;
import pl.edu.pk.pkampus.modules.receptionist.dto.UpdateIssueStatusRequestDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E: Issue Reporting and Resolution Lifecycle (Report -> Review -> In Progress -> Resolved -> Verify)")
class IssueReportingAndResolutionE2ETest {

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
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private Room room;
    private User resident;
    private User receptionist;

    @BeforeEach
    void setUp() {
        when(minioStorageService.uploadIssuePhoto(any())).thenReturn("issue-photo-key.jpg");
        when(minioStorageService.getIssuePresignedUrl(anyString(), anyInt()))
                .thenReturn("https://minio.local/issues/issue-photo-key.jpg");

        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Awaria E2E")
                .code("AW-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Techniczna 5")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("204")
                .floor(2)
                .capacity(2)
                .build());

        resident = userRepository.save(User.builder()
                .email("res-issue-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Marek")
                .lastName("Zgłaszający")
                .phoneNumber("+48501502503")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("204")
                .build());

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        receptionist = userRepository.save(User.builder()
                .email("portier-issue-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Stanisław")
                .lastName("Dyżurny")
                .phoneNumber("+48601602603")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());
    }

    @Test
    @DisplayName("Complete Issue Lifecycle: Report Issue -> Receptionist Reviews -> In Progress -> Resolved -> Resident Verifies")
    void completeIssueLifecycle() throws Exception {
        String residentBearer = "Bearer " + jwtService.generateToken(resident, "204");
        String receptionistBearer = "Bearer " + jwtService.generateToken(receptionist, null);

        // -------------------------------------------------------------
        // KROK 1: Mieszkaniec zgłasza usterkę hydrauliczną ze zdjęciem (UC-ISS-01)
        // -------------------------------------------------------------
        CreateIssueRequestDto issueDto = new CreateIssueRequestDto(
                IssueLocationType.MY_ROOM,
                null,
                IssueCategory.PLUMBING,
                IssueUrgency.NORMAL,
                "Zawór pod grzejnikiem przecieka na podłogę."
        );

        MockMultipartFile dataPart = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(issueDto)
        );

        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile photoPart = new MockMultipartFile(
                "photo",
                "valve.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                validJpeg
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/v1/issues")
                        .file(dataPart)
                        .file(photoPart)
                        .header("Authorization", residentBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.description").value("Zawór pod grzejnikiem przecieka na podłogę."))
                .andReturn();

        String issueIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID issueId = UUID.fromString(issueIdStr);

        // -------------------------------------------------------------
        // KROK 2: Portier przegląda listę zgłoszeń dla swojego akademika (UC-REC-02)
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/receptionist/issues")
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(issueId)).exists());

        // -------------------------------------------------------------
        // KROK 3: Portier pobiera szczegóły usterki ze zdjęciem presigned URL
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/receptionist/issues/{id}", issueId)
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.photoUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("NEW"));

        // -------------------------------------------------------------
        // KROK 4: Portier przypisuje zgłoszenie do konserwatora (NEW -> ASSIGNED_TO_MAINTENANCE)
        // -------------------------------------------------------------
        UpdateIssueStatusRequestDto assignDto = new UpdateIssueStatusRequestDto(
                IssueStatus.ASSIGNED_TO_MAINTENANCE,
                "Przydzielono hydraulikowi dyżurnemu"
        );

        mockMvc.perform(patch("/api/v1/receptionist/issues/{id}/status", issueId)
                        .header("Authorization", receptionistBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED_TO_MAINTENANCE"))
                .andExpect(jsonPath("$.data.staffNotes").value("Przydzielono hydraulikowi dyżurnemu"));

        // -------------------------------------------------------------
        // KROK 5: Konserwator podejmuje pracę (ASSIGNED_TO_MAINTENANCE -> IN_PROGRESS)
        // -------------------------------------------------------------
        UpdateIssueStatusRequestDto inProgressDto = new UpdateIssueStatusRequestDto(
                IssueStatus.IN_PROGRESS,
                "Hydraulik powiadomiony, przyjdzie około 13:00"
        );

        mockMvc.perform(patch("/api/v1/receptionist/issues/{id}/status", issueId)
                        .header("Authorization", receptionistBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inProgressDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.staffNotes").value("Hydraulik powiadomiony, przyjdzie około 13:00"));

        // -------------------------------------------------------------
        // KROK 6: Portier oznacza usterkę jako usuniętą (IN_PROGRESS -> RESOLVED)
        // -------------------------------------------------------------
        UpdateIssueStatusRequestDto resolvedDto = new UpdateIssueStatusRequestDto(
                IssueStatus.RESOLVED,
                "Uszczelka i zawór wymienione. Wyciek usunięty."
        );

        mockMvc.perform(patch("/api/v1/receptionist/issues/{id}/status", issueId)
                        .header("Authorization", receptionistBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolvedDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.staffNotes").value("Uszczelka i zawór wymienione. Wyciek usunięty."));

        // -------------------------------------------------------------
        // KROK 7: Mieszkaniec sprawdza listę swoich zgłoszeń i widzi status RESOLVED
        // -------------------------------------------------------------
        mockMvc.perform(get("/api/v1/issues/me")
                        .header("Authorization", residentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].status".formatted(issueId)).value("RESOLVED"))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].staffNotes".formatted(issueId))
                        .value("Uszczelka i zawór wymienione. Wyciek usunięty."));

        assertThat(issueRepository.findById(issueId).orElseThrow().getStatus())
                .isEqualTo(IssueStatus.RESOLVED);
    }
}

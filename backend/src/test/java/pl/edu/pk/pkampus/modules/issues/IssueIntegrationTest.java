package pl.edu.pk.pkampus.modules.issues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Resident issues API integration tests")
class IssueIntegrationTest {

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
    private IssueRepository issueRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MinioStorageService minioStorageService;

    @MockBean
    private EmailService emailService;

    private Dormitory dorm;
    private Room room;
    private User resident;
    private User residentNoAssignment;
    private User otherResident;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Issues")
                .code("IS-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        room = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("305")
                .floor(3)
                .capacity(2)
                .build());

        resident = saveResident(dorm, "305");
        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        residentNoAssignment = saveResident(dorm, "999");
        otherResident = saveResident(dorm, "306");
        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(otherResident)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        when(minioStorageService.uploadIssuePhoto(any())).thenReturn("issue-photo-obj.jpg");
        when(minioStorageService.getIssuePresignedUrl(anyString(), anyInt()))
                .thenReturn("https://minio.example/issues/issue-photo-obj.jpg");
    }

    @Test
    @DisplayName("Create MY_ROOM issue and list me")
    void createMyRoomAndList() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "locationType": "MY_ROOM",
                          "category": "PLUMBING",
                          "urgency": "URGENT",
                          "description": "Cieknie kran w łazience"
                        }
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(data)
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.locationLabel").value("Pokój 305"))
                .andExpect(jsonPath("$.data.category").value("PLUMBING"))
                .andExpect(jsonPath("$.data.hasPhoto").value(false));

        mockMvc.perform(get("/api/v1/issues/me")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].description").value("Cieknie kran w łazience"));
    }

    @Test
    @DisplayName("Create COMMON_AREA issue with photo")
    void createCommonAreaWithPhoto() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "locationType": "COMMON_AREA",
                          "commonAreaName": "pralnia",
                          "category": "ELECTRICAL",
                          "urgency": "NORMAL",
                          "description": "Nie działa oświetlenie"
                        }
                        """.getBytes()
        );
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "lamp.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(data)
                        .file(photo)
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commonAreaName").value("pralnia"))
                .andExpect(jsonPath("$.data.hasPhoto").value(true))
                .andExpect(jsonPath("$.data.photoUrl").value("https://minio.example/issues/issue-photo-obj.jpg"));
    }

    @Test
    @DisplayName("MY_ROOM without assignment returns 422")
    void myRoomWithoutAssignment() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "locationType": "MY_ROOM",
                          "category": "OTHER",
                          "urgency": "NORMAL",
                          "description": "Problem"
                        }
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(data)
                        .header("Authorization", bearer(residentNoAssignment)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Invalid common area returns 422")
    void invalidCommonArea() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "locationType": "COMMON_AREA",
                          "commonAreaName": "basen",
                          "category": "OTHER",
                          "urgency": "NORMAL",
                          "description": "Problem"
                        }
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/issues")
                        .file(data)
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Photo of another resident's issue returns 404")
    void foreignPhotoNotFound() throws Exception {
        Issue issue = issueRepository.save(Issue.builder()
                .reporter(resident)
                .dormitory(dorm)
                .room(room)
                .category(IssueCategory.FURNITURE)
                .urgency(IssueUrgency.NORMAL)
                .description("Szafa")
                .status(IssueStatus.NEW)
                .build());
        issue.addPhoto(IssuePhoto.builder()
                .photoUrl("secret.jpg")
                .fileName("secret.jpg")
                .fileSizeBytes(100)
                .build());
        issueRepository.save(issue);

        mockMvc.perform(get("/api/v1/issues/" + issue.getId() + "/photo")
                        .header("Authorization", bearer(otherResident)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Own photo redirects to presigned URL")
    void ownPhotoRedirect() throws Exception {
        Issue issue = issueRepository.save(Issue.builder()
                .reporter(resident)
                .dormitory(dorm)
                .commonAreaName("winda")
                .category(IssueCategory.OTHER)
                .urgency(IssueUrgency.NORMAL)
                .description("Winda stoi")
                .status(IssueStatus.NEW)
                .build());
        issue.addPhoto(IssuePhoto.builder()
                .photoUrl("issue-photo-obj.jpg")
                .fileName("winda.jpg")
                .fileSizeBytes(200)
                .build());
        issueRepository.save(issue);

        mockMvc.perform(get("/api/v1/issues/" + issue.getId() + "/photo")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://minio.example/issues/issue-photo-obj.jpg"));
    }

    private User saveResident(Dormitory dormitory, String declaredRoom) {
        return userRepository.save(User.builder()
                .email("issue-res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("Resident")
                .phoneNumber("+48111111111")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber(declaredRoom)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }
}

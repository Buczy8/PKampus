package pl.edu.pk.pkampus.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import pl.edu.pk.pkampus.modules.board.Comment;
import pl.edu.pk.pkampus.modules.board.CommentRepository;
import pl.edu.pk.pkampus.modules.board.Post;
import pl.edu.pk.pkampus.modules.board.PostCategory;
import pl.edu.pk.pkampus.modules.board.PostRepository;
import pl.edu.pk.pkampus.modules.board.PostScope;
import pl.edu.pk.pkampus.modules.board.PostStatus;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.issues.Issue;
import pl.edu.pk.pkampus.modules.issues.IssueCategory;
import pl.edu.pk.pkampus.modules.issues.IssuePhoto;
import pl.edu.pk.pkampus.modules.issues.IssuePhotoRepository;
import pl.edu.pk.pkampus.modules.issues.IssueRepository;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.issues.IssueUrgency;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;
import pl.edu.pk.pkampus.modules.retention.DataRetentionService;
import pl.edu.pk.pkampus.modules.retention.dto.DataRetentionReportDto;
import pl.edu.pk.pkampus.modules.rooms.RoomBooking;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingRepository;
import pl.edu.pk.pkampus.modules.rooms.RoomBookingStatus;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoom;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomRepository;
import pl.edu.pk.pkampus.modules.rooms.ThematicRoomStatus;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GDPR / RODO data retention integration tests (§12)")
class DataRetentionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataRetentionService dataRetentionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssuePhotoRepository issuePhotoRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LaundryMachineRepository laundryMachineRepository;

    @Autowired
    private LaundryBookingRepository laundryBookingRepository;

    @Autowired
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    private Dormitory dormitory;
    private User resident;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        dormitory = dormitoryRepository.save(Dormitory.builder()
                .name("DS-1 Olimp")
                .code("DS1-RET")
                .address("ul. Skarżyńskiego 5")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(6, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(60)
                .build());

        resident = userRepository.save(User.builder()
                .email("resident.retention@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48123456789")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dormitory)
                .declaredRoomNumber("101")
                .build());

        superAdmin = userRepository.save(User.builder()
                .email("superadmin.retention@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Super")
                .lastName("Admin")
                .phoneNumber("+48999888777")
                .role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Should purge issue photos in MinIO and DB for RESOLVED issues older than 30 days while keeping Issue")
    void shouldPurgeOldIssuePhotos() {
        Issue oldResolvedIssue = issueRepository.save(Issue.builder()
                .reporter(resident)
                .dormitory(dormitory)
                .category(IssueCategory.PLUMBING)
                .urgency(IssueUrgency.NORMAL)
                .description("Cieknący kran")
                .status(IssueStatus.RESOLVED)
                .build());

        IssuePhoto photoOld = issuePhotoRepository.save(IssuePhoto.builder()
                .issue(oldResolvedIssue)
                .fileName("kran.jpg")
                .photoUrl("issues/old-resolved-photo.jpg")
                .fileSizeBytes(1024)
                .build());

        Issue freshResolvedIssue = issueRepository.save(Issue.builder()
                .reporter(resident)
                .dormitory(dormitory)
                .category(IssueCategory.ELECTRICAL)
                .urgency(IssueUrgency.NORMAL)
                .description("Awaria gniazdka")
                .status(IssueStatus.RESOLVED)
                .build());

        IssuePhoto photoFresh = issuePhotoRepository.save(IssuePhoto.builder()
                .issue(freshResolvedIssue)
                .fileName("gniazdko.jpg")
                .photoUrl("issues/fresh-resolved-photo.jpg")
                .fileSizeBytes(2048)
                .build());

        entityManager.flush();

        // Simulate old issue resolved 35 days ago
        Instant oldTimestamp = Instant.now().minus(35, ChronoUnit.DAYS);
        entityManager.createNativeQuery("UPDATE issues SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", oldTimestamp)
                .setParameter("id", oldResolvedIssue.getId())
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();
        entityManager.flush();
        entityManager.clear();

        assertThat(report.getIssuePhotosRemovedCount()).isGreaterThanOrEqualTo(1);
        verify(minioStorageService).removeIssuePhoto("issues/old-resolved-photo.jpg");

        assertThat(issuePhotoRepository.findById(photoOld.getId())).isEmpty();
        assertThat(issuePhotoRepository.findById(photoFresh.getId())).isPresent();
        assertThat(issueRepository.findById(oldResolvedIssue.getId())).isPresent();
    }

    @Test
    @DisplayName("Should permanently remove old resolved (>30d) and soft-deleted (>14d) posts and comments")
    void shouldPurgeOldBoardPostsAndComments() {
        Post oldResolvedPost = postRepository.save(Post.builder()
                .author(resident)
                .dormitory(dormitory)
                .title("Kupię lodówkę")
                .content("Stan dobry")
                .category(PostCategory.BUY_SELL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.RESOLVED)
                .build());

        Comment commentOnOldPost = commentRepository.save(Comment.builder()
                .post(oldResolvedPost)
                .author(resident)
                .content("Aktualne?")
                .build());

        Post oldDeletedPost = postRepository.save(Post.builder()
                .author(resident)
                .dormitory(dormitory)
                .title("Zgubiono klucze")
                .content("Czarne klucze")
                .category(PostCategory.LOST_FOUND)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(true)
                .build());

        Post activePost = postRepository.save(Post.builder()
                .author(resident)
                .dormitory(dormitory)
                .title("Pytanie o pralkę")
                .content("Kto ma żetony?")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.ACTIVE)
                .deleted(false)
                .build());

        Comment oldDeletedComment = commentRepository.save(Comment.builder()
                .post(activePost)
                .author(resident)
                .content("Komentarz do usunięcia")
                .deleted(true)
                .build());

        Comment activeComment = commentRepository.save(Comment.builder()
                .post(activePost)
                .author(resident)
                .content("Komentarz aktywny")
                .deleted(false)
                .build());

        entityManager.flush();

        Instant ts35d = Instant.now().minus(35, ChronoUnit.DAYS);
        Instant ts16d = Instant.now().minus(16, ChronoUnit.DAYS);

        entityManager.createNativeQuery("UPDATE posts SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", ts35d)
                .setParameter("id", oldResolvedPost.getId())
                .executeUpdate();

        entityManager.createNativeQuery("UPDATE posts SET deleted_at = :ts WHERE id = :id")
                .setParameter("ts", ts16d)
                .setParameter("id", oldDeletedPost.getId())
                .executeUpdate();

        entityManager.createNativeQuery("UPDATE comments SET deleted_at = :ts WHERE id = :id")
                .setParameter("ts", ts16d)
                .setParameter("id", oldDeletedComment.getId())
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();
        entityManager.flush();
        entityManager.clear();

        assertThat(report.getPostsRemovedCount()).isGreaterThanOrEqualTo(2);
        assertThat(report.getCommentsRemovedCount()).isGreaterThanOrEqualTo(1);

        assertThat(postRepository.findById(oldResolvedPost.getId())).isEmpty();
        assertThat(commentRepository.findById(commentOnOldPost.getId())).isEmpty();
        assertThat(postRepository.findById(oldDeletedPost.getId())).isEmpty();
        assertThat(postRepository.findById(activePost.getId())).isPresent();
        assertThat(commentRepository.findById(oldDeletedComment.getId())).isEmpty();
        assertThat(commentRepository.findById(activeComment.getId())).isPresent();
    }

    @Test
    @DisplayName("Should purge completed and cancelled laundry/room bookings older than 90 days")
    void shouldPurgeOldBookings() {
        LaundryMachine machine = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dormitory)
                .machineIdentifier("PRALKA-RET-01")
                .floorLocation("1")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        ThematicRoom room = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dormitory)
                .name("Salka Cichej Nauki")
                .maxCapacity(10)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        Instant pastStart = Instant.now().minus(95, ChronoUnit.DAYS);
        Instant pastEnd = pastStart.plus(1, ChronoUnit.HOURS);

        LaundryBooking oldCompletedLaundry = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .status(LaundryBookingStatus.COMPLETED)
                .build());

        LaundryBooking freshCompletedLaundry = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine)
                .user(resident)
                .startTime(Instant.now().minus(10, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(10, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(LaundryBookingStatus.COMPLETED)
                .build());

        RoomBooking oldAutoCancelledRoom = roomBookingRepository.save(RoomBooking.builder()
                .room(room)
                .user(resident)
                .startTime(pastStart)
                .endTime(pastEnd)
                .participantsCount(2)
                .purpose("Nauka fizyki")
                .status(RoomBookingStatus.AUTO_CANCELLED_15MIN)
                .termsAccepted(true)
                .build());

        RoomBooking freshConfirmedRoom = roomBookingRepository.save(RoomBooking.builder()
                .room(room)
                .user(resident)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .participantsCount(3)
                .purpose("Nauka matematyki")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        entityManager.flush();
        entityManager.clear();

        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();
        entityManager.flush();
        entityManager.clear();

        assertThat(report.getLaundryBookingsPurgedCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.getRoomBookingsPurgedCount()).isGreaterThanOrEqualTo(1);

        assertThat(laundryBookingRepository.findById(oldCompletedLaundry.getId())).isEmpty();
        assertThat(laundryBookingRepository.findById(freshCompletedLaundry.getId())).isPresent();
        assertThat(roomBookingRepository.findById(oldAutoCancelledRoom.getId())).isEmpty();
        assertThat(roomBookingRepository.findById(freshConfirmedRoom.getId())).isPresent();
    }

    @Test
    @DisplayName("Should anonymize checked-out student accounts older than 365 days")
    void shouldAnonymizeOldCheckedOutUsers() {
        User oldCheckedOutUser = userRepository.save(User.builder()
                .email("student.checkedout.old@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Piotr")
                .lastName("Zieliński")
                .phoneNumber("+48500600700")
                .role(UserRole.RESIDENT)
                .status(UserStatus.CHECKED_OUT)
                .dormitory(dormitory)
                .declaredRoomNumber("205")
                .build());

        User recentCheckedOutUser = userRepository.save(User.builder()
                .email("student.checkedout.recent@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Anna")
                .lastName("Kowalska")
                .phoneNumber("+48500600701")
                .role(UserRole.RESIDENT)
                .status(UserStatus.CHECKED_OUT)
                .dormitory(dormitory)
                .declaredRoomNumber("206")
                .build());

        entityManager.flush();

        Instant oldTimestamp = Instant.now().minus(380, ChronoUnit.DAYS);
        entityManager.createNativeQuery("UPDATE users SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", oldTimestamp)
                .setParameter("id", oldCheckedOutUser.getId())
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        DataRetentionReportDto report = dataRetentionService.runRetentionTasks();

        assertThat(report.getUsersAnonymizedCount()).isGreaterThanOrEqualTo(1);

        User anonymized = userRepository.findById(oldCheckedOutUser.getId()).orElseThrow();
        assertThat(anonymized.getFirstName()).isEqualTo("Anonim");
        assertThat(anonymized.getLastName()).isEqualTo("Użytkownik");
        assertThat(anonymized.getEmail()).isEqualTo("anonymized-" + oldCheckedOutUser.getId() + "@pkampus.local");
        assertThat(anonymized.getPhoneNumber()).isEqualTo("000000000");
        assertThat(anonymized.getDeclaredRoomNumber()).isNull();

        User recent = userRepository.findById(recentCheckedOutUser.getId()).orElseThrow();
        assertThat(recent.getFirstName()).isEqualTo("Anna");
        assertThat(recent.getEmail()).isEqualTo("student.checkedout.recent@pk.edu.pl");
    }

    @Test
    @DisplayName("SuperAdmin endpoint POST /api/v1/superadmin/retention/run should require SUPER_ADMIN role")
    void testSuperAdminEndpointAccess() throws Exception {
        String residentToken = jwtService.generateToken(resident);
        String superAdminToken = jwtService.generateToken(superAdmin);

        // 403 for RESIDENT
        mockMvc.perform(post("/api/v1/superadmin/retention/run")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // 200 for SUPER_ADMIN
        mockMvc.perform(post("/api/v1/superadmin/retention/run")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.executedAt").exists())
                .andExpect(jsonPath("$.data.executionDurationMs").isNumber());
    }
}

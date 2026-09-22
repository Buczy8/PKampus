package pl.edu.pk.pkampus.modules.board;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.JwtService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
@DisplayName("Resident board posts API integration tests")
class PostIntegrationTest {

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
    private PostRepository postRepository;

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
    private User resident1;
    private User resident2OtherDorm;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Board 1")
                .code("B1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Board 2")
                .code("B2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Testowa 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        Room room = roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("312")
                .floor(3)
                .capacity(2)
                .build());

        resident1 = saveResident(dorm1, "312");
        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident1)
                .room(room)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        resident2OtherDorm = saveResident(dorm2, "101");
    }

    @Test
    @DisplayName("Create DORMITORY post and list feed with room number")
    void createDormitoryAndList() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Pożyczę wiertarkę",
                                  "content": "Na weekend, pokój 312",
                                  "category": "BORROW_HELP",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.scope").value("DORMITORY"))
                .andExpect(jsonPath("$.data.authorRoomNumber").value("312"))
                .andExpect(jsonPath("$.data.mine").value(true));

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Pożyczę wiertarkę"));
    }

    @Test
    @DisplayName("CAMPUS post hides room number")
    void campusHidesRoom() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Sprzedam biurko",
                                  "content": "Stan dobry",
                                  "category": "BUY_SELL",
                                  "scope": "CAMPUS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorRoomNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.authorDormitoryName").value("DS Board 1"));

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(resident2OtherDorm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].authorRoomNumber").value(nullValue()));
    }

    @Test
    @DisplayName("Other dorm DORMITORY post is not visible")
    void otherDormHidden() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Lokalne",
                                  "content": "Tylko DS1",
                                  "category": "GENERAL",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(resident2OtherDorm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Author can resolve; stranger cannot")
    void resolveOwnOnly() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Zgubiłem klucze",
                                  "content": "Przy windzie",
                                  "category": "LOST_FOUND",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/posts/" + id + "/resolve")
                        .header("Authorization", bearer(resident2OtherDorm)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/posts/" + id + "/resolve")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/posts")
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/v1/posts")
                        .param("status", "RESOLVED")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Author soft-deletes post")
    void softDelete() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Do usunięcia",
                                  "content": "Test",
                                  "category": "GENERAL",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/posts/" + id)
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk());

        assertThat(postRepository.findById(UUID.fromString(id)))
                .get()
                .extracting(Post::isDeleted)
                .isEqualTo(true);

        mockMvc.perform(get("/api/v1/posts")
                        .param("status", "ALL")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Comments can be added and listed; campus hides room on comment")
    void commentsThread() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Potrzebuję pomocy",
                                  "content": "Ktoś ma przedłużacz?",
                                  "category": "BORROW_HELP",
                                  "scope": "CAMPUS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/posts/" + id + "/comments")
                        .header("Authorization", bearer(resident2OtherDorm))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Mogę pożyczyć, napisz na priv" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Mogę pożyczyć, napisz na priv"))
                .andExpect(jsonPath("$.data.authorRoomNumber").value(nullValue()));

        mockMvc.perform(get("/api/v1/posts/" + id + "/comments")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commentCount").value(1));
    }

    @Test
    @DisplayName("Unauthenticated request to board returns 401")
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-resident (e.g. DORM_ADMIN) cannot access resident board API")
    void nonResidentForbidden() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("board-admin-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Marian")
                .lastName("Kierownik")
                .phoneNumber("+48999888777")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Blocked resident cannot access community board")
    void blockedResidentForbidden() throws Exception {
        User blocked = userRepository.save(User.builder()
                .email("blocked-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Zablokowany")
                .lastName("Student")
                .phoneNumber("+48555666777")
                .role(UserRole.RESIDENT)
                .status(UserStatus.BLOCKED)
                .dormitory(dorm1)
                .build());

        mockMvc.perform(get("/api/v1/posts")
                        .header("Authorization", bearer(blocked)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Blank post creation rejected with 400 Bad Request")
    void blankPostRejected() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "content": "",
                                  "category": "GENERAL",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resolving already resolved post returns 422 Unprocessable Entity")
    void doubleResolveFails() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Do rozwiązania",
                                  "content": "Test",
                                  "category": "GENERAL",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/posts/" + id + "/resolve")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/posts/" + id + "/resolve")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Cannot comment on REMOVED_MODERATOR post")
    void cannotCommentOnModeratedPost() throws Exception {
        Post post = postRepository.save(Post.builder()
                .author(resident1)
                .dormitory(dorm1)
                .title("Zmoderowany")
                .content("Naruszenie")
                .category(PostCategory.GENERAL)
                .scope(PostScope.DORMITORY)
                .status(PostStatus.REMOVED_MODERATOR)
                .deleted(false)
                .build());

        mockMvc.perform(post("/api/v1/posts/" + post.getId() + "/comments")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Komentarz\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Feed filters accurately by category and scope")
    void feedFiltering() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Kupię rower",
                                  "content": "Stan bdb",
                                  "category": "BUY_SELL",
                                  "scope": "CAMPUS"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(resident1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Pożyczę odkurzacz",
                                  "content": "Na godzinę",
                                  "category": "BORROW_HELP",
                                  "scope": "DORMITORY"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/posts")
                        .param("category", "BUY_SELL")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Kupię rower"));

        mockMvc.perform(get("/api/v1/posts")
                        .param("scope", "DORMITORY")
                        .header("Authorization", bearer(resident1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Pożyczę odkurzacz"));
    }

    private User saveResident(Dormitory dorm, String room) {
        return userRepository.save(User.builder()
                .email("board-res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48111111111")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber(room)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }
}

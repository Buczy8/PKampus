package pl.edu.pk.pkampus.modules.admin.dormrooms;

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
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
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
@DisplayName("ADS dorm rooms API integration tests")
class AdminDormRoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
                .name("DS Rooms 1")
                .code("R1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Pokoj 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Rooms 2")
                .code("R2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Pokoj 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(180)
                .build());

        dormAdmin1 = saveUser("ads-rm-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.DORM_ADMIN, dorm1, null);
        resident1 = saveUser("res-rm-" + UUID.randomUUID() + "@pk.edu.pl", UserRole.RESIDENT, dorm1, "101");
    }

    @Test
    @DisplayName("DORM_ADMIN can create and list rooms in own dormitory")
    void adminCreatesAndLists() throws Exception {
        mockMvc.perform(post("/api/v1/admin/dorm-rooms")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"204","floor":2,"capacity":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomNumber").value("204"))
                .andExpect(jsonPath("$.data.floor").value(2))
                .andExpect(jsonPath("$.data.capacity").value(2));

        mockMvc.perform(get("/api/v1/admin/dorm-rooms")
                        .header("Authorization", bearer(dormAdmin1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Duplicate room number in same dormitory is rejected")
    void duplicateRoomNumberRejected() throws Exception {
        roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());

        mockMvc.perform(post("/api/v1/admin/dorm-rooms")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","floor":1,"capacity":2}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Floor above dormitory floorsCount is rejected")
    void floorAboveLimitRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/dorm-rooms")
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"501","floor":5,"capacity":2}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DORM_ADMIN can update room capacity")
    void adminUpdatesRoom() throws Exception {
        Room room = roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("303")
                .floor(3)
                .capacity(2)
                .build());

        mockMvc.perform(patch("/api/v1/admin/dorm-rooms/" + room.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capacity":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(3));
    }

    @Test
    @DisplayName("Room from another dormitory returns 404")
    void otherDormRoomNotFound() throws Exception {
        Room other = roomRepository.save(Room.builder()
                .dormitory(dorm2)
                .roomNumber("201")
                .floor(2)
                .capacity(2)
                .build());

        mockMvc.perform(patch("/api/v1/admin/dorm-rooms/" + other.getId())
                        .header("Authorization", bearer(dormAdmin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capacity":1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RESIDENT cannot access dorm rooms admin API")
    void residentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dorm-rooms")
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
        return "Bearer " + jwtService.generateToken(user);
    }
}

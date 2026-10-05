package pl.edu.pk.pkampus.modules.dormitory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Dormitory module integration tests")
class DormitoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Dormitory dorm1;
    private Dormitory dorm2;
    private Room room101;
    private Room room202;
    private User resident;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS-1 Testowy")
                .code("T1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Akademicka 1")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(120)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS-2 Testowy")
                .code("T2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Akademicka 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(8, 0))
                .laundryClosingTime(LocalTime.of(21, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        room101 = roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("101")
                .floor(1)
                .capacity(2)
                .build());

        room202 = roomRepository.save(Room.builder()
                .dormitory(dorm1)
                .roomNumber("202")
                .floor(2)
                .capacity(3)
                .build());

        resident = userRepository.save(User.builder()
                .email("student-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Michał")
                .lastName("Kowalski")
                .phoneNumber("+48123123123")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("101")
                .build());
    }

    @Test
    @DisplayName("Public endpoint GET /api/v1/dormitories returns 200 without authentication")
    void getDormitoriesPublicAccess() throws Exception {
        mockMvc.perform(get("/api/v1/dormitories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Dormitories are returned in alphabetical order by name")
    void getDormitoriesAlphabeticalOrder() throws Exception {
        dormitoryRepository.save(Dormitory.builder()
                .name("AAA First")
                .code("T0-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Akademicka 0")
                .floorsCount(2)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(22, 0))
                .laundrySlotDurationMinutes(120)
                .build());

        mockMvc.perform(get("/api/v1/dormitories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("AAA First"));
    }

    @Test
    @DisplayName("DormitoryRepository findByCode returns matching dormitory")
    void dormitoryRepositoryFindByCode() {
        Optional<Dormitory> found = dormitoryRepository.findByCode(dorm1.getCode());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("DS-1 Testowy");

        Optional<Dormitory> notFound = dormitoryRepository.findByCode("NON_EXISTENT_CODE");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("RoomRepository custom queries work accurately")
    void roomRepositoryQueries() {
        // findByDormitoryIdAndRoomNumber
        Optional<Room> foundRoom = roomRepository.findByDormitoryIdAndRoomNumber(dorm1.getId(), "101");
        assertThat(foundRoom).isPresent();
        assertThat(foundRoom.get().getId()).isEqualTo(room101.getId());

        // findByIdAndDormitoryId
        Optional<Room> correctDorm = roomRepository.findByIdAndDormitoryId(room101.getId(), dorm1.getId());
        assertThat(correctDorm).isPresent();
        Optional<Room> wrongDorm = roomRepository.findByIdAndDormitoryId(room101.getId(), dorm2.getId());
        assertThat(wrongDorm).isEmpty();

        // findAllByDormitoryIdOrderByFloorAscRoomNumberAsc
        List<Room> orderedRooms = roomRepository.findAllByDormitoryIdOrderByFloorAscRoomNumberAsc(dorm1.getId());
        assertThat(orderedRooms).hasSize(2);
        assertThat(orderedRooms.get(0).getFloor()).isLessThanOrEqualTo(orderedRooms.get(1).getFloor());

        // existsByDormitoryIdAndRoomNumber
        assertThat(roomRepository.existsByDormitoryIdAndRoomNumber(dorm1.getId(), "101")).isTrue();
        assertThat(roomRepository.existsByDormitoryIdAndRoomNumber(dorm1.getId(), "999")).isFalse();

        // existsByDormitoryIdAndRoomNumberAndIdNot
        assertThat(roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dorm1.getId(), "101", room101.getId())).isFalse();
        assertThat(roomRepository.existsByDormitoryIdAndRoomNumberAndIdNot(dorm1.getId(), "101", room202.getId())).isTrue();
    }

    @Test
    @DisplayName("RoomAssignmentRepository custom queries work accurately")
    void roomAssignmentRepositoryQueries() {
        RoomAssignment assignment = roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(room101)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        // findByUserIdAndIsActiveTrue
        Optional<RoomAssignment> active = roomAssignmentRepository.findByUserIdAndIsActiveTrue(resident.getId());
        assertThat(active).isPresent();
        assertThat(active.get().getId()).isEqualTo(assignment.getId());
        assertThat(active.get().getRoom().getRoomNumber()).isEqualTo("101");

        // findAllByUserId
        List<RoomAssignment> allForUser = roomAssignmentRepository.findAllByUserId(resident.getId());
        assertThat(allForUser).hasSize(1);

        // findAllByRoomIdAndIsActiveTrue
        List<RoomAssignment> roomActive = roomAssignmentRepository.findAllByRoomIdAndIsActiveTrue(room101.getId());
        assertThat(roomActive).hasSize(1);
        assertThat(roomAssignmentRepository.findAllByRoomIdAndIsActiveTrue(room202.getId())).isEmpty();
    }
}

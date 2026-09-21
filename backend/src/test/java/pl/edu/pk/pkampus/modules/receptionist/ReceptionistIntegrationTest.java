package pl.edu.pk.pkampus.modules.receptionist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.issues.IssueRepository;
import pl.edu.pk.pkampus.modules.issues.IssueStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryBooking;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryBookingStatus;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachine;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineRepository;
import pl.edu.pk.pkampus.modules.laundry.LaundryMachineStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Receptionist desk API integration tests")
class ReceptionistIntegrationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DormitoryRepository dormitoryRepository;

    @Autowired
    private LaundryMachineRepository laundryMachineRepository;

    @Autowired
    private LaundryBookingRepository laundryBookingRepository;

    @Autowired
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

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

    private Dormitory dorm1;
    private Dormitory dorm2;
    private LaundryMachine machine1;
    private LaundryMachine machineOtherDorm;
    private ThematicRoom room1;
    private User resident;
    private User receptionist;
    private User receptionistOtherDorm;
    private User otherResident;

    @BeforeEach
    void setUp() {
        dorm1 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Desk 1")
                .code("D1-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Portierska 1")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        dorm2 = dormitoryRepository.save(Dormitory.builder()
                .name("DS Desk 2")
                .code("D2-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Portierska 2")
                .floorsCount(3)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        machine1 = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm1)
                .machineIdentifier("Washer Desk A")
                .floorLocation("Ground")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        machineOtherDorm = laundryMachineRepository.save(LaundryMachine.builder()
                .dormitory(dorm2)
                .machineIdentifier("Washer Desk X")
                .floorLocation("Basement")
                .status(LaundryMachineStatus.AVAILABLE)
                .build());

        room1 = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm1)
                .name("Sala Nauki Desk")
                .maxCapacity(10)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(22, 0))
                .spansMidnight(false)
                .maxDurationHours(4)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        resident = userRepository.save(User.builder()
                .email("desk-res-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Jan")
                .lastName("Kowalski")
                .phoneNumber("+48111111111")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .declaredRoomNumber("101")
                .build());

        otherResident = userRepository.save(User.builder()
                .email("desk-res2-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Anna")
                .lastName("Nowak")
                .phoneNumber("+48222222222")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm2)
                .declaredRoomNumber("201")
                .build());

        receptionist = userRepository.save(User.builder()
                .email("desk-port-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Stanisław")
                .lastName("Portier")
                .phoneNumber("+48333333333")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm1)
                .build());

        receptionistOtherDorm = userRepository.save(User.builder()
                .email("desk-port2-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password1!"))
                .firstName("Piotr")
                .lastName("Portier2")
                .phoneNumber("+48444444444")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm2)
                .build());
    }

    @Test
    @DisplayName("GET /desk returns today's laundry/room bookings for receptionist dorm")
    void deskSnapshot() throws Exception {
        Instant start = todayAt(LocalTime.of(10, 0));
        Instant end = start.plus(90, ChronoUnit.MINUTES);

        laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(start)
                .endTime(end)
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(12, 0)))
                .endTime(todayAt(LocalTime.of(14, 0)))
                .participantsCount(3)
                .purpose("Nauka")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        mockMvc.perform(get("/api/v1/receptionist/desk")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.laundry", hasSize(1)))
                .andExpect(jsonPath("$.data.laundry[0].residentFirstName").value("Jan"))
                .andExpect(jsonPath("$.data.laundry[0].machineIdentifier").value("Washer Desk A"))
                .andExpect(jsonPath("$.data.rooms", hasSize(1)))
                .andExpect(jsonPath("$.data.rooms[0].roomName").value("Sala Nauki Desk"))
                .andExpect(jsonPath("$.data.openIssuesCount").value(0));
    }

    @Test
    @DisplayName("RESIDENT cannot access receptionist desk")
    void residentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/receptionist/desk")
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Issue then return laundry key")
    void issueAndReturnLaundryKey() throws Exception {
        LaundryBooking booking = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(11, 0)))
                .endTime(todayAt(LocalTime.of(12, 30)))
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + booking.getId() + "/issue-key")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("KEY_ISSUED"))
                .andExpect(jsonPath("$.data.keyIssuedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + booking.getId() + "/return-key")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Issue room key")
    void issueRoomKey() throws Exception {
        RoomBooking booking = roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(15, 0)))
                .endTime(todayAt(LocalTime.of(17, 0)))
                .participantsCount(2)
                .purpose("Spotkanie")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + booking.getId() + "/issue-key")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("KEY_ISSUED"))
                .andExpect(jsonPath("$.data.keyIssuedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Booking from another dormitory returns 404")
    void otherDormitoryNotFound() throws Exception {
        LaundryBooking foreign = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machineOtherDorm)
                .user(otherResident)
                .startTime(todayAt(LocalTime.of(9, 0)))
                .endTime(todayAt(LocalTime.of(10, 30)))
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + foreign.getId() + "/issue-key")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + foreign.getId() + "/issue-key")
                        .header("Authorization", bearer(receptionistOtherDorm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("KEY_ISSUED"));
    }

    @Test
    @DisplayName("GET laundry schedule for receptionist; resident forbidden")
    void laundryScheduleAccess() throws Exception {
        LocalDate from = LocalDate.now(WARSAW);
        LocalDate to = from.plusDays(1);

        mockMvc.perform(get("/api/v1/receptionist/laundry/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.machines.length()").value(1));

        mockMvc.perform(get("/api/v1/receptionist/laundry/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancel CONFIRMED laundry; KEY_ISSUED returns 422")
    void cancelLaundryBooking() throws Exception {
        LaundryBooking confirmed = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(18, 0)))
                .endTime(todayAt(LocalTime.of(19, 30)))
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + confirmed.getId() + "/cancel")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"));

        LaundryBooking issued = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(20, 0)))
                .endTime(todayAt(LocalTime.of(21, 30)))
                .status(LaundryBookingStatus.KEY_ISSUED)
                .keyIssuedAt(Instant.now())
                .build());

        mockMvc.perform(post("/api/v1/receptionist/laundry/" + issued.getId() + "/cancel")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Machine breakdown cancels future CONFIRMED, keeps KEY_ISSUED, creates issue")
    void machineBreakdownCascade() throws Exception {
        Instant futureStart = Instant.now().plus(2, ChronoUnit.HOURS);
        LaundryBooking future = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(futureStart)
                .endTime(futureStart.plus(90, ChronoUnit.MINUTES))
                .status(LaundryBookingStatus.CONFIRMED)
                .build());

        Instant issuedStart = Instant.now().minus(30, ChronoUnit.MINUTES);
        LaundryBooking issued = laundryBookingRepository.save(LaundryBooking.builder()
                .machine(machine1)
                .user(resident)
                .startTime(issuedStart)
                .endTime(issuedStart.plus(90, ChronoUnit.MINUTES))
                .status(LaundryBookingStatus.KEY_ISSUED)
                .keyIssuedAt(Instant.now().minus(25, ChronoUnit.MINUTES))
                .build());

        mockMvc.perform(post("/api/v1/receptionist/laundry/machines/" + machine1.getId() + "/breakdown")
                        .header("Authorization", bearer(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Water leak\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.machineId").value(machine1.getId().toString()))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.issueId").isNotEmpty());

        assertThat(laundryBookingRepository.findById(future.getId())).get()
                .extracting(LaundryBooking::getStatus)
                .isEqualTo(LaundryBookingStatus.CANCELLED_MACHINE_OUT_OF_ORDER);
        assertThat(laundryBookingRepository.findById(issued.getId())).get()
                .extracting(LaundryBooking::getStatus)
                .isEqualTo(LaundryBookingStatus.KEY_ISSUED);
        assertThat(laundryMachineRepository.findById(machine1.getId())).get()
                .extracting(LaundryMachine::getStatus)
                .isEqualTo(LaundryMachineStatus.OUT_OF_ORDER);

        assertThat(issueRepository.findAll()).anySatisfy(issue -> {
            assertThat(issue.getStatus()).isEqualTo(IssueStatus.NEW);
            assertThat(issue.getCommonAreaName()).isEqualTo("pralnia");
            assertThat(issue.getReporter().getId()).isEqualTo(receptionist.getId());
        });

        verify(emailService).sendLaundryMachineBreakdownEmail(
                anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/receptionist/laundry/machines/" + machine1.getId() + "/restore")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET room schedule for receptionist; resident forbidden")
    void roomScheduleAccess() throws Exception {
        LocalDate from = LocalDate.now(WARSAW);
        LocalDate to = from.plusDays(1);

        mockMvc.perform(get("/api/v1/receptionist/rooms/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rooms.length()").value(1));

        mockMvc.perform(get("/api/v1/receptionist/rooms/schedule")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", bearer(resident)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancel CONFIRMED room; KEY_ISSUED returns 422")
    void cancelRoomBooking() throws Exception {
        RoomBooking confirmed = roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(18, 0)))
                .endTime(todayAt(LocalTime.of(20, 0)))
                .participantsCount(2)
                .purpose("Nauka")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + confirmed.getId() + "/cancel")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED_USER"));

        RoomBooking issued = roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(todayAt(LocalTime.of(20, 0)))
                .endTime(todayAt(LocalTime.of(22, 0)))
                .participantsCount(2)
                .purpose("Spotkanie")
                .status(RoomBookingStatus.KEY_ISSUED)
                .keyIssuedAt(Instant.now())
                .termsAccepted(true)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + issued.getId() + "/cancel")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Room maintenance cancels future CONFIRMED, keeps KEY_ISSUED, creates issue")
    void roomMaintenanceCascade() throws Exception {
        Instant futureStart = Instant.now().plus(2, ChronoUnit.HOURS);
        RoomBooking future = roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(futureStart)
                .endTime(futureStart.plus(2, ChronoUnit.HOURS))
                .participantsCount(3)
                .purpose("Nauka")
                .status(RoomBookingStatus.CONFIRMED)
                .termsAccepted(true)
                .build());

        Instant issuedStart = Instant.now().minus(30, ChronoUnit.MINUTES);
        RoomBooking issued = roomBookingRepository.save(RoomBooking.builder()
                .room(room1)
                .user(resident)
                .startTime(issuedStart)
                .endTime(issuedStart.plus(2, ChronoUnit.HOURS))
                .participantsCount(2)
                .purpose("Spotkanie")
                .status(RoomBookingStatus.KEY_ISSUED)
                .keyIssuedAt(Instant.now().minus(25, ChronoUnit.MINUTES))
                .termsAccepted(true)
                .build());

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + room1.getId() + "/maintenance")
                        .header("Authorization", bearer(receptionist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Broken projector\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(room1.getId().toString()))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.issueId").isNotEmpty());

        assertThat(roomBookingRepository.findById(future.getId())).get()
                .extracting(RoomBooking::getStatus)
                .isEqualTo(RoomBookingStatus.CANCELLED_ROOM_MAINTENANCE);
        assertThat(roomBookingRepository.findById(issued.getId())).get()
                .extracting(RoomBooking::getStatus)
                .isEqualTo(RoomBookingStatus.KEY_ISSUED);
        assertThat(thematicRoomRepository.findById(room1.getId())).get()
                .extracting(ThematicRoom::getStatus)
                .isEqualTo(ThematicRoomStatus.MAINTENANCE);

        assertThat(issueRepository.findAll()).anySatisfy(issue -> {
            assertThat(issue.getStatus()).isEqualTo(IssueStatus.NEW);
            assertThat(issue.getCommonAreaName()).isEqualTo("inne");
            assertThat(issue.getReporter().getId()).isEqualTo(receptionist.getId());
        });

        verify(emailService).sendRoomMaintenanceEmail(
                anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/receptionist/rooms/" + room1.getId() + "/restore")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    private Instant todayAt(LocalTime time) {
        return LocalDate.now(WARSAW).atTime(time).atZone(WARSAW).toInstant();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user, user.getDeclaredRoomNumber());
    }
}

package pl.edu.pk.pkampus.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.mail.EmailService;
import pl.edu.pk.pkampus.modules.admin.dto.CreateRoomBanRequestDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E: Thematic Room Booking & Sanction Lifecycle (Book -> Issue Key -> Return Key -> Issue ROOM_BAN -> Ban Enforced -> Revoke Ban)")
class ThematicRoomBookingAndSanctionE2ETest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

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
    private ThematicRoomRepository thematicRoomRepository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MinioStorageService minioStorageService;

    @MockitoBean
    private EmailService emailService;

    private Dormitory dorm;
    private ThematicRoom gymRoom;
    private User resident;
    private User receptionist;
    private User dormAdmin;

    @BeforeEach
    void setUp() {
        dorm = dormitoryRepository.save(Dormitory.builder()
                .name("DS Salka E2E")
                .code("SK-" + UUID.randomUUID().toString().substring(0, 4))
                .address("ul. Akademicka 15")
                .floorsCount(4)
                .laundryOpeningTime(LocalTime.of(7, 0))
                .laundryClosingTime(LocalTime.of(23, 0))
                .laundrySlotDurationMinutes(90)
                .build());

        Room dormRoom = roomRepository.save(Room.builder()
                .dormitory(dorm)
                .roomNumber("110")
                .floor(1)
                .capacity(2)
                .build());

        gymRoom = thematicRoomRepository.save(ThematicRoom.builder()
                .dormitory(dorm)
                .name("Siłownia Studencka")
                .maxCapacity(6)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 0))
                .spansMidnight(false)
                .maxDurationHours(3)
                .status(ThematicRoomStatus.AVAILABLE)
                .build());

        resident = userRepository.save(User.builder()
                .email("res-room-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Grzegorz")
                .lastName("Sportowiec")
                .phoneNumber("+48505506507")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .declaredRoomNumber("110")
                .build());

        roomAssignmentRepository.save(RoomAssignment.builder()
                .user(resident)
                .room(dormRoom)
                .academicYear("2025/2026")
                .isActive(true)
                .checkInDate(LocalDate.now().minusMonths(1))
                .build());

        receptionist = userRepository.save(User.builder()
                .email("portier-room-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Antoni")
                .lastName("Portier")
                .phoneNumber("+48605606607")
                .role(UserRole.RECEPTIONIST)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());

        dormAdmin = userRepository.save(User.builder()
                .email("admin-room-" + UUID.randomUUID() + "@pk.edu.pl")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .firstName("Barbara")
                .lastName("Kierowniczka")
                .phoneNumber("+48705706707")
                .role(UserRole.DORM_ADMIN)
                .status(UserStatus.ACTIVE)
                .dormitory(dorm)
                .build());
    }

    @Test
    @DisplayName("Complete Room Booking & Sanctions Flow: Book -> Issue Key -> Return Key -> Impose Ban -> Booking Rejected -> Revoke Ban -> Booking Allowed")
    void completeRoomBookingAndSanctionsFlow() throws Exception {
        String residentBearer = "Bearer " + jwtService.generateToken(resident, "110");
        String receptionistBearer = "Bearer " + jwtService.generateToken(receptionist, null);
        String adminBearer = "Bearer " + jwtService.generateToken(dormAdmin, null);

        LocalDate tomorrow = LocalDate.now(WARSAW).plusDays(1);
        OffsetDateTime start1 = tomorrow.atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
        OffsetDateTime end1 = tomorrow.atTime(12, 0).atZone(WARSAW).toOffsetDateTime();

        // -------------------------------------------------------------
        // KROK 1: Rezerwacja salki tematycznej przez mieszkańca (UC-ROOM-01)
        // -------------------------------------------------------------
        MvcResult bookingResult = mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomBookingJson(gymRoom.getId(), start1, end1, 2, "Trening obwodowy")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.roomId").value(gymRoom.getId().toString()))
                .andReturn();

        String bookingIdStr = objectMapper.readTree(bookingResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID bookingId = UUID.fromString(bookingIdStr);

        // -------------------------------------------------------------
        // KROK 2: Portier wydaje klucz do salki (CONFIRMED -> KEY_ISSUED)
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/receptionist/rooms/{id}/issue-key", bookingId)
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("KEY_ISSUED"));

        RoomBooking booking = roomBookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(RoomBookingStatus.KEY_ISSUED);

        // -------------------------------------------------------------
        // KROK 3: Portier przyjmuje zwrot klucza (KEY_ISSUED -> COMPLETED)
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/receptionist/rooms/{id}/return-key", bookingId)
                        .header("Authorization", receptionistBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        booking = roomBookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(RoomBookingStatus.COMPLETED);

        // -------------------------------------------------------------
        // KROK 4: Administrator DS nakłada sankcję ROOM_BAN na mieszkańca (np. za nieporządek)
        // -------------------------------------------------------------
        CreateRoomBanRequestDto roomBanDto = CreateRoomBanRequestDto.builder()
                .durationMonths(1)
                .reason("Pozostawienie nieporządku i ciężarów po treningu w siłowni")
                .build();

        MvcResult banResult = mockMvc.perform(post("/api/v1/admin/residents/{id}/room-ban", resident.getId())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBanDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sanctionType").value("ROOM_BAN"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        String sanctionIdStr = objectMapper.readTree(banResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID sanctionId = UUID.fromString(sanctionIdStr);

        // -------------------------------------------------------------
        // KROK 5: Próba kolejnej rezerwacji przez ukaranego mieszkańca zostaje odrzucona (BR-08)
        // -------------------------------------------------------------
        OffsetDateTime start2 = tomorrow.plusDays(1).atTime(10, 0).atZone(WARSAW).toOffsetDateTime();
        OffsetDateTime end2 = start2.plusHours(2);

        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomBookingJson(gymRoom.getId(), start2, end2, 2, "Kolejny trening")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // -------------------------------------------------------------
        // KROK 6: Administrator DS cofa nałożoną sankcję (odwołanie / wyjaśnienie)
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/admin/residents/{id}/room-ban/{sanctionId}/revoke", resident.getId(), sanctionId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));

        // -------------------------------------------------------------
        // KROK 7: Po zdjęciu kary rezerwacja salki znowu jest możliwa
        // -------------------------------------------------------------
        mockMvc.perform(post("/api/v1/rooms/bookings")
                        .header("Authorization", residentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomBookingJson(gymRoom.getId(), start2, end2, 2, "Kolejny trening po wyjaśnieniu sprawy")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    private String roomBookingJson(UUID roomId, OffsetDateTime start, OffsetDateTime end, int participants, String purpose) throws Exception {
        return objectMapper.createObjectNode()
                .put("roomId", roomId.toString())
                .put("startTime", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("endTime", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("participantsCount", participants)
                .put("purpose", purpose)
                .put("termsAccepted", true)
                .toString();
    }
}

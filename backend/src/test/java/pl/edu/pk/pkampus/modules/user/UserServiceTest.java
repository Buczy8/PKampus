package pl.edu.pk.pkampus.modules.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Room;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignment;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("getUserProfile")
    class GetUserProfileTests {

        @Test
        @DisplayName("Throws ResourceNotFoundException when user does not exist")
        void throwsExceptionWhenUserNotFound() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.getUserProfile(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with ID: " + userId);
        }

        @Test
        @DisplayName("Returns profile with active room assignment when available")
        void returnsProfileWithActiveRoomAssignment() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID dormId = UUID.randomUUID();
            Instant now = Instant.now();

            Dormitory dorm = Dormitory.builder()
                    .id(dormId)
                    .name("DS Bartek")
                    .build();

            User user = User.builder()
                    .id(userId)
                    .email("student@pk.edu.pl")
                    .firstName("Adam")
                    .lastName("Nowak")
                    .phoneNumber("+48123456789")
                    .avatarUrl("https://minio/avatar.png")
                    .role(UserRole.RESIDENT)
                    .status(UserStatus.ACTIVE)
                    .dormitory(dorm)
                    .declaredRoomNumber("101-OLD")
                    .createdAt(now)
                    .build();

            Room room = Room.builder()
                    .roomNumber("205-ASSIGNED")
                    .build();

            RoomAssignment assignment = RoomAssignment.builder()
                    .room(room)
                    .isActive(true)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(Optional.of(assignment));

            // Act
            UserProfileDto profile = userService.getUserProfile(userId);

            // Assert
            assertThat(profile).isNotNull();
            assertThat(profile.getId()).isEqualTo(userId);
            assertThat(profile.getEmail()).isEqualTo("student@pk.edu.pl");
            assertThat(profile.getFirstName()).isEqualTo("Adam");
            assertThat(profile.getLastName()).isEqualTo("Nowak");
            assertThat(profile.getPhoneNumber()).isEqualTo("+48123456789");
            assertThat(profile.getAvatarUrl()).isEqualTo("https://minio/avatar.png");
            assertThat(profile.getRole()).isEqualTo(UserRole.RESIDENT);
            assertThat(profile.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(profile.getDormitoryId()).isEqualTo(dormId);
            assertThat(profile.getDormitoryName()).isEqualTo("DS Bartek");
            assertThat(profile.getRoomNumber()).isEqualTo("205-ASSIGNED");
            assertThat(profile.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Falls back to declared room number when no active room assignment exists")
        void fallsBackToDeclaredRoomNumber() {
            // Arrange
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("student2@pk.edu.pl")
                    .firstName("Kasia")
                    .lastName("Zielińska")
                    .declaredRoomNumber("302A")
                    .dormitory(null)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(roomAssignmentRepository.findByUserIdAndIsActiveTrue(userId)).thenReturn(Optional.empty());

            // Act
            UserProfileDto profile = userService.getUserProfile(userId);

            // Assert
            assertThat(profile.getRoomNumber()).isEqualTo("302A");
            assertThat(profile.getDormitoryId()).isNull();
            assertThat(profile.getDormitoryName()).isNull();
        }
    }
}

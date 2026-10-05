package pl.edu.pk.pkampus.modules.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        return toProfileDto(user, roomNumber);
    }

    private UserProfileDto toProfileDto(User user, String roomNumber) {
        return UserProfileDto.from(user, roomNumber);
    }
}

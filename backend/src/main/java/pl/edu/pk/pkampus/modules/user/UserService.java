package pl.edu.pk.pkampus.modules.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Nie znaleziono użytkownika o adresie: " + username));
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono użytkownika o podanym ID"));

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        return toProfileDto(user, roomNumber);
    }

    public UserProfileDto toProfileDto(User user, String roomNumber) {
        UUID dormId = null;
        String dormName = null;
        if (user.getDormitory() != null) {
            dormId = user.getDormitory().getId();
            dormName = user.getDormitory().getName();
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .dormitoryId(dormId)
                .dormitoryName(dormName)
                .roomNumber(roomNumber)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

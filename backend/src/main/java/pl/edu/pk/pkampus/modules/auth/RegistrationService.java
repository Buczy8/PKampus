package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.token.EmailTokenPayload;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    public static final String REGISTRATION_SUCCESS_MESSAGE =
            "Registration request received. If the email is eligible, an activation link has been sent to your inbox.";

    private final UserRepository userRepository;
    private final DormitoryRepository dormitoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService minioStorageService;
    private final SignedEmailTokenService signedEmailTokenService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Orchestrates registration without holding a DB transaction during MinIO I/O.
     * Only {@link #persistPendingUser} runs transactionally; the verification
     * email is dispatched after commit (see {@link RegistrationMailListener}).
     */
    public RegisterResponseDto registerResident(RegisterRequestDto dto, MultipartFile photo) {
        // Always validate image first (equal early work for anti-enumeration timing)
        String detectedMime = minioStorageService.validateAndDetectImageType(photo);
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt with existing email: {}", normalizedEmail);
            passwordEncoder.encode(dto.getPassword());
            return new RegisterResponseDto(REGISTRATION_SUCCESS_MESSAGE, normalizedEmail);
        }

        Dormitory dormitory = dormitoryRepository.findById(dto.getDormitoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dormitory not found with the provided ID"));

        String avatarUrl = minioStorageService.uploadAvatar(photo, detectedMime);

        try {
            return transactionTemplate.execute(status ->
                    persistPendingUser(dto, normalizedEmail, dormitory, avatarUrl));
        } catch (Exception e) {
            log.error("Failed to complete resident registration for {}. Compensating by removing uploaded avatar {}",
                    normalizedEmail, avatarUrl, e);
            deleteAvatarQuietly(avatarUrl);
            throw e;
        }
    }

    private RegisterResponseDto persistPendingUser(
            RegisterRequestDto dto, String normalizedEmail, Dormitory dormitory, String avatarUrl) {
        User savedUser = userRepository.save(buildPendingUser(dto, normalizedEmail, dormitory, avatarUrl));

        eventPublisher.publishEvent(new ResidentRegisteredEvent(savedUser.getId(), savedUser.getEmail()));

        log.info("Resident registered with ID {}. Verification email dispatched.", savedUser.getId());

        return new RegisterResponseDto(REGISTRATION_SUCCESS_MESSAGE, savedUser.getEmail());
    }

    @Transactional
    public VerifyEmailResponseDto verifyEmail(String token) {
        EmailTokenPayload payload = signedEmailTokenService.verifyToken(token);

        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User associated with the token does not exist"));

        if (!user.getEmail().equalsIgnoreCase(payload.email())) {
            throw new AccountStatusException("Email address mismatch in activation token");
        }

        if (user.getStatus() == UserStatus.PENDING_EMAIL) {
            user.setStatus(UserStatus.PENDING_APPROVAL);
            userRepository.save(user);
            log.info("User {} successfully verified email. Account set to PENDING_APPROVAL.", user.getId());
        }

        return new VerifyEmailResponseDto(
                "Email address confirmed successfully. Your account is awaiting residency approval by the dormitory administration.",
                user.getStatus()
        );
    }

    private User buildPendingUser(
            RegisterRequestDto dto, String normalizedEmail, Dormitory dormitory, String avatarUrl) {
        return User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .phoneNumber(dto.getPhoneNumber().trim())
                .avatarUrl(avatarUrl)
                .role(UserRole.RESIDENT)
                .status(UserStatus.PENDING_EMAIL)
                .dormitory(dormitory)
                .declaredRoomNumber(dto.getDeclaredRoomNumber().trim())
                .build();
    }

    private void deleteAvatarQuietly(String avatarUrl) {
        try {
            minioStorageService.removeAvatar(avatarUrl);
        } catch (Exception minioEx) {
            log.error("Failed to cleanup orphaned avatar {} from MinIO", avatarUrl, minioEx);
        }
    }
}

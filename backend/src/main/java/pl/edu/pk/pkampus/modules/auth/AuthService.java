package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.dormitory.DormitoryRepository;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.security.token.SignedEmailTokenService;
import pl.edu.pk.pkampus.security.token.EmailTokenPayload;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.RefreshTokenService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;
import pl.edu.pk.pkampus.mail.EmailService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DormitoryRepository dormitoryRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService minioStorageService;
    private final SignedEmailTokenService signedEmailTokenService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final EmailService emailService;

    public static final String REGISTRATION_SUCCESS_MESSAGE =
            "Registration request received. If the email is eligible, an activation link has been sent to your inbox.";

    @Transactional
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
            User user = User.builder()
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

            User savedUser = userRepository.save(user);

            String token = signedEmailTokenService.generateToken(savedUser.getId(), savedUser.getEmail());
            emailService.sendVerificationEmail(savedUser.getEmail(), token);

            log.info("Resident registered with ID {}. Verification email dispatched.", savedUser.getId());

            return new RegisterResponseDto(REGISTRATION_SUCCESS_MESSAGE, savedUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to complete resident registration for {}. Compensating by removing uploaded avatar {}",
                    normalizedEmail, avatarUrl, e);
            try {
                minioStorageService.removeAvatar(avatarUrl);
            } catch (Exception minioEx) {
                log.error("Failed to cleanup orphaned avatar {} from MinIO", avatarUrl, minioEx);
            }
            throw e;
        }
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

    @Transactional
    public AuthResponseDto login(LoginRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        switch (user.getStatus()) {
            case PENDING_EMAIL ->
                    throw new AccountStatusException("Please confirm your email address by clicking the link sent to your inbox.");
            case PENDING_APPROVAL ->
                    throw new AccountStatusException("Your account is awaiting residency approval by the dormitory administration.");
            case BLOCKED ->
                    throw new AccountStatusException("Account has been administratively suspended. Please contact the dormitory manager.");
            case CHECKED_OUT ->
                    throw new AccountStatusException("Account has expired (checked out). Please contact the dormitory administration.");
            case ACTIVE -> {
                // proceed
            }
        }

        // Logout blacklists access JWTs for ~TTL; clear so a fresh login works immediately.
        tokenRevocationService.clearRevocation(user.getId());
        // Drop previous refresh sessions so only this login's token remains current.
        refreshTokenService.revokeAllUserTokens(user.getId());

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        String jwt = jwtService.generateToken(user, roomNumber);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        UserProfileDto profile = buildUserProfileDto(user, roomNumber);

        log.info("User {} successfully logged in.", user.getId());

        return AuthResponseDto.builder()
                .token(jwt)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .refreshToken(refreshToken)
                .refreshExpiresInSeconds(refreshTokenService.getRefreshExpirationSeconds())
                .user(profile)
                .build();
    }

    @Transactional
    public AuthResponseDto refreshToken(String rawRefreshToken) {
        RefreshTokenService.RefreshTokenResult result = refreshTokenService.rotateRefreshToken(rawRefreshToken);
        User user = result.user();

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        String newJwt = jwtService.generateToken(user, roomNumber);
        UserProfileDto profile = buildUserProfileDto(user, roomNumber);

        return AuthResponseDto.builder()
                .token(newJwt)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .refreshToken(result.newRawToken())
                .refreshExpiresInSeconds(refreshTokenService.getRefreshExpirationSeconds())
                .user(profile)
                .build();
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        boolean hasUser = userId != null;
        boolean hasRefresh = rawRefreshToken != null && !rawRefreshToken.isBlank();

        if (!hasUser && !hasRefresh) {
            throw new IllegalArgumentException("Logout requires a Bearer access token or a refresh token");
        }

        if (hasUser) {
            tokenRevocationService.revokeUser(userId);
        }

        if (hasRefresh) {
            refreshTokenService.revokeRefreshToken(rawRefreshToken).ifPresent(tokenOwnerId -> {
                if (!hasUser) {
                    tokenRevocationService.blacklistAccessToken(tokenOwnerId);
                }
            });
        }

        log.info("User {} successfully logged out.", userId);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        return buildUserProfileDto(user, roomNumber);
    }

    public UserProfileDto buildUserProfileDto(User user, String roomNumber) {
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

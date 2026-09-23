package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
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
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.RefreshTokenService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;
import pl.edu.pk.pkampus.mail.EmailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DormitoryRepository dormitoryRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService minioStorageService;
    private final SignedEmailTokenService signedEmailTokenService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthenticatedUserCache authenticatedUserCache;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public static final String REGISTRATION_SUCCESS_MESSAGE =
            "Registration request received. If the email is eligible, an activation link has been sent to your inbox.";

    public static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If an account associated with this email address exists, a password reset link has been sent.";

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
            case ACTIVE, MUST_CHANGE_PASSWORD -> {
                // proceed
            }
        }

        // Logout blacklists access JWTs for ~TTL; clear so a fresh login works immediately.
        tokenRevocationService.clearRevocation(user.getId());
        // Drop previous refresh sessions so only this login's token remains current.
        refreshTokenService.revokeAllUserTokens(user.getId());

        String roomNumber = resolveRoomNumber(user);

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

        String roomNumber = resolveRoomNumber(user);

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

        String roomNumber = resolveRoomNumber(user);

        return buildUserProfileDto(user, roomNumber);
    }

    @Transactional
    public UserProfileDto changePassword(User principal, ChangePasswordRequestDto dto) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.MUST_CHANGE_PASSWORD) {
            throw new AccountStatusException("Password cannot be changed for this account status");
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        if (user.getStatus() == UserStatus.MUST_CHANGE_PASSWORD) {
            user.setStatus(UserStatus.ACTIVE);
        }
        User saved = userRepository.save(user);
        authenticatedUserCache.invalidate(saved.getId());

        String roomNumber = resolveRoomNumber(saved);

        log.info("User {} changed password (status={})", saved.getId(), saved.getStatus());
        return buildUserProfileDto(saved, roomNumber);
    }

    public UserProfileDto buildUserProfileDto(User user, String roomNumber) {
        return UserProfileDto.from(user, roomNumber);
    }

    private String resolveRoomNumber(User user) {
        return roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());
    }

    @Transactional
    public String initiatePasswordReset(ForgotPasswordRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        Optional<User> optionalUser = userRepository.findByEmail(normalizedEmail);

        if (optionalUser.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", normalizedEmail);
            return FORGOT_PASSWORD_GENERIC_MESSAGE;
        }

        User user = optionalUser.get();
        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.CHECKED_OUT) {
            log.warn("Password reset requested for suspended/checked-out user: {}", user.getId());
            return FORGOT_PASSWORD_GENERIC_MESSAGE;
        }

        // Invalidate previous active reset tokens for this user
        passwordResetTokenRepository.invalidateAllActiveForUser(user.getId(), Instant.now());

        // Generate 32-byte secure random token (64 hex characters)
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = sha256Hex(rawToken);

        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
        log.info("Password reset token generated and email dispatched for user {}", user.getId());

        return FORGOT_PASSWORD_GENERIC_MESSAGE;
    }

    @Transactional(readOnly = true)
    public VerifyResetTokenResponseDto verifyResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token must not be blank");
        }

        String tokenHash = sha256Hex(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired password reset token"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        String maskedEmail = maskEmail(token.getUser().getEmail());
        return new VerifyResetTokenResponseDto(true, maskedEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDto dto) {
        String tokenHash = sha256Hex(dto.getToken().trim());
        PasswordResetToken token = passwordResetTokenRepository.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired password reset token"));

        Instant now = Instant.now();
        if (token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        User user = token.getUser();
        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.CHECKED_OUT) {
            throw new AccountStatusException("Account is suspended or checked out");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        if (user.getStatus() == UserStatus.MUST_CHANGE_PASSWORD) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        // Invalidate all active tokens and sessions for this user
        tokenRevocationService.revokeUser(user.getId());

        log.info("User {} successfully reset password via email token", user.getId());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}

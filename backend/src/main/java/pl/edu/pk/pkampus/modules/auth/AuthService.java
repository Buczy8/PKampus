package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.modules.auth.dto.AuthResponseDto;
import pl.edu.pk.pkampus.modules.auth.dto.ChangePasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.LoginRequestDto;
import pl.edu.pk.pkampus.modules.user.dto.UserProfileDto;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.dormitory.RoomAssignmentRepository;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.security.jwt.AuthenticatedUserCache;
import pl.edu.pk.pkampus.security.jwt.JwtService;
import pl.edu.pk.pkampus.security.jwt.RefreshTokenService;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthenticatedUserCache authenticatedUserCache;

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

        String jwt = jwtService.generateToken(user);
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

        String newJwt = jwtService.generateToken(user);
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

        user.applyNewPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        User saved = userRepository.save(user);
        authenticatedUserCache.invalidate(saved.getId());

        String roomNumber = resolveRoomNumber(saved);

        log.info("User {} changed password (status={})", saved.getId(), saved.getStatus());
        return buildUserProfileDto(saved, roomNumber);
    }

    private UserProfileDto buildUserProfileDto(User user, String roomNumber) {
        return UserProfileDto.from(user, roomNumber);
    }

    private String resolveRoomNumber(User user) {
        return roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());
    }
}

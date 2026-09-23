package pl.edu.pk.pkampus.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.common.exception.AccountStatusException;
import pl.edu.pk.pkampus.common.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.modules.auth.dto.ForgotPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.ResetPasswordRequestDto;
import pl.edu.pk.pkampus.modules.auth.dto.VerifyResetTokenResponseDto;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserStatus;
import pl.edu.pk.pkampus.security.jwt.TokenRevocationService;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    public static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If an account associated with this email address exists, a password reset link has been sent.";

    private static final Set<UserStatus> RESETTABLE_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.MUST_CHANGE_PASSWORD);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenRevocationService tokenRevocationService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void initiatePasswordReset(ForgotPasswordRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();
        Optional<User> optionalUser = userRepository.findByEmail(normalizedEmail);

        if (optionalUser.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", normalizedEmail);
            return;
        }

        User user = optionalUser.get();
        if (!RESETTABLE_STATUSES.contains(user.getStatus())) {
            log.warn("Password reset requested for non-eligible user {} (status={})", user.getId(), user.getStatus());
            return;
        }

        // Invalidate previous active reset tokens for this user
        Instant now = clock.instant();
        passwordResetTokenRepository.invalidateAllActiveForUser(user.getId(), now);

        String rawToken = ResetTokenSupport.generateRawToken();
        String tokenHash = ResetTokenSupport.sha256Hex(rawToken);

        Instant expiresAt = now.plus(ResetTokenSupport.RESET_TOKEN_TTL);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        passwordResetTokenRepository.save(resetToken);

        eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getFirstName(), rawToken));
        log.info("Password reset token generated and email dispatched for user {}", user.getId());
    }

    @Transactional(readOnly = true)
    public VerifyResetTokenResponseDto verifyResetToken(String rawToken) {
        PasswordResetToken token = requireUsableToken(rawToken, clock.instant());

        String maskedEmail = ResetTokenSupport.maskEmail(token.getUser().getEmail());
        return new VerifyResetTokenResponseDto(true, maskedEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDto dto) {
        Instant now = clock.instant();
        PasswordResetToken token = requireUsableToken(dto.getToken(), now);

        User user = token.getUser();
        if (!RESETTABLE_STATUSES.contains(user.getStatus())) {
            throw new AccountStatusException("Account is not eligible for password reset");
        }

        // Consume the token first so a concurrent request with the same token changes nothing.
        if (passwordResetTokenRepository.markUsedIfUnused(token.getId(), now) == 0) {
            throw new ResourceNotFoundException("Invalid or expired password reset token");
        }

        user.applyNewPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active tokens and sessions for this user
        tokenRevocationService.revokeUser(user.getId());

        log.info("User {} successfully reset password via email token", user.getId());
    }

    private PasswordResetToken requireUsableToken(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token must not be blank");
        }

        String tokenHash = ResetTokenSupport.sha256Hex(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findActiveByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired password reset token"));

        if (token.isExpired(now)) {
            throw new IllegalArgumentException("Password reset token has expired");
        }
        return token;
    }
}

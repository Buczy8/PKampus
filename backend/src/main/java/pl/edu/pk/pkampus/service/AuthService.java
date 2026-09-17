package pl.edu.pk.pkampus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.dto.AuthResponseDto;
import pl.edu.pk.pkampus.dto.LoginRequestDto;
import pl.edu.pk.pkampus.dto.RegisterRequestDto;
import pl.edu.pk.pkampus.dto.RegisterResponseDto;
import pl.edu.pk.pkampus.dto.UserProfileDto;
import pl.edu.pk.pkampus.dto.VerifyEmailResponseDto;
import pl.edu.pk.pkampus.exception.AccountStatusException;
import pl.edu.pk.pkampus.exception.ResourceNotFoundException;
import pl.edu.pk.pkampus.model.Dormitory;
import pl.edu.pk.pkampus.model.User;
import pl.edu.pk.pkampus.model.UserRole;
import pl.edu.pk.pkampus.model.UserStatus;
import pl.edu.pk.pkampus.repository.DormitoryRepository;
import pl.edu.pk.pkampus.repository.RoomAssignmentRepository;
import pl.edu.pk.pkampus.repository.UserRepository;

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
    private final EmailService emailService;

    @Transactional
    public RegisterResponseDto registerResident(RegisterRequestDto dto, MultipartFile photo) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        // Anti-enumeration protection (UC-AUTH-01): if email already exists, return neutral message
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt with existing email: {}", normalizedEmail);
            return new RegisterResponseDto(
                    "Jeśli podany adres e-mail nie istnieje w systemie, wysłaliśmy link aktywacyjny.",
                    normalizedEmail
            );
        }

        Dormitory dormitory = dormitoryRepository.findById(dto.getDormitoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono akademika o podanym identyfikatorze"));

        // Upload avatar to MinIO S3
        String avatarUrl = minioStorageService.uploadAvatar(photo);

        // Create resident in PENDING_EMAIL state
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

        // Generate signed HMAC token (TTL 24h) and send email
        String token = signedEmailTokenService.generateToken(savedUser.getId(), savedUser.getEmail());
        emailService.sendVerificationEmail(savedUser.getEmail(), token);

        log.info("Resident registered with ID {}. Verification email dispatched.", savedUser.getId());

        return new RegisterResponseDto(
                "Rejestracja powiodła się. Sprawdź swoją skrzynkę pocztową, aby potwierdzić adres e-mail.",
                savedUser.getEmail()
        );
    }

    @Transactional
    public VerifyEmailResponseDto verifyEmail(String token) {
        EmailTokenPayload payload = signedEmailTokenService.verifyToken(token);

        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik powiązany z tokenem nie istnieje"));

        if (!user.getEmail().equalsIgnoreCase(payload.email())) {
            throw new AccountStatusException("Niezgodność adresu e-mail w tokenie aktywacyjnym");
        }

        if (user.getStatus() == UserStatus.PENDING_EMAIL) {
            user.setStatus(UserStatus.PENDING_APPROVAL);
            userRepository.save(user);
            log.info("User {} successfully verified email. Account set to PENDING_APPROVAL.", user.getId());
        }

        return new VerifyEmailResponseDto(
                "Adres e-mail został pomyślnie potwierdzony. Twoje konto oczekuje na weryfikację meldunku przez Administrację DS.",
                user.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Nieprawidłowy e-mail lub hasło"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Nieprawidłowy e-mail lub hasło");
        }

        // Validate account status against lifecycle rules (BR-06, FR-AUTH-03)
        switch (user.getStatus()) {
            case PENDING_EMAIL ->
                    throw new AccountStatusException("Potwierdź swój adres e-mail klikając w link przesłany na pocztę.");
            case PENDING_APPROVAL ->
                    throw new AccountStatusException("Twoje konto oczekuje na weryfikację meldunku przez Administrację DS.");
            case BLOCKED ->
                    throw new AccountStatusException("Konto zostało zablokowane administracyjnie. Skontaktuj się z kierownikiem DS.");
            case CHECKED_OUT ->
                    throw new AccountStatusException("Konto wygasło (wymeldowanie). Skontaktuj się z administracją DS.");
            case ACTIVE -> {
                // Account is active, proceed to login
            }
        }

        // Determine room number from active room assignment if present
        String roomNumber = roomAssignmentRepository.findByUserIdAndIsActiveTrue(user.getId())
                .map(ra -> ra.getRoom().getRoomNumber())
                .orElse(user.getDeclaredRoomNumber());

        String jwt = jwtService.generateToken(user, roomNumber);
        UserProfileDto profile = buildUserProfileDto(user, roomNumber);

        log.info("User {} successfully logged in.", user.getId());

        return AuthResponseDto.builder()
                .token(jwt)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .user(profile)
                .build();
    }

    public UserProfileDto buildUserProfileDto(User user, String roomNumber) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .dormitoryId(user.getDormitory() != null ? user.getDormitory().getId() : null)
                .dormitoryName(user.getDormitory() != null ? user.getDormitory().getName() : null)
                .roomNumber(roomNumber)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

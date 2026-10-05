package pl.edu.pk.pkampus.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRepository;
import pl.edu.pk.pkampus.modules.user.UserRole;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigUnitTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private MustChangePasswordFilter mustChangePasswordFilter;

    @Mock
    private pl.edu.pk.pkampus.security.ratelimit.AuthRateLimitFilter authRateLimitFilter;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Environment environment;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                jwtAuthenticationFilter,
                mustChangePasswordFilter,
                authRateLimitFilter,
                userRepository,
                environment,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCreatePasswordEncoderWithStrength12() {
        // Arrange & Act
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        // Assert
        assertNotNull(encoder);
        String raw = "StrongPassword123!";
        String encoded = encoder.encode(raw);
        assertTrue(encoded.startsWith("$2a$12$") || encoded.startsWith("$2b$12$"));
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void shouldLoadUserByUsernameWhenUserExists() {
        // Arrange
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("student@pk.edu.pl")
                .passwordHash("hash")
                .role(UserRole.RESIDENT)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findByEmail("student@pk.edu.pl")).thenReturn(Optional.of(user));

        UserDetailsService userDetailsService = securityConfig.userDetailsService();

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("  STUDENT@PK.EDU.PL  ");

        // Assert
        assertNotNull(userDetails);
        assertEquals("student@pk.edu.pl", userDetails.getUsername());
        verify(userRepository).findByEmail("student@pk.edu.pl");
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserDoesNotExist() {
        // Arrange
        when(userRepository.findByEmail("nonexistent@pk.edu.pl")).thenReturn(Optional.empty());
        UserDetailsService userDetailsService = securityConfig.userDetailsService();

        // Act & Assert
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("nonexistent@pk.edu.pl"));
    }

    @Test
    void shouldProvideValidCorsConfiguration() {
        // Arrange
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");

        // Act
        CorsConfiguration config = source.getCorsConfiguration(request);

        // Assert
        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("http://localhost:5173"));
        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(config.getAllowedMethods().contains("GET"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowCredentials());
    }

    @Test
    void shouldUseConfiguredCorsOriginsWhenPropertyIsSet() {
        // Arrange
        when(environment.getProperty("app.security.cors.allowed-origins"))
                .thenReturn("https://kampus.pk.edu.pl, https://app.kampus.pk.edu.pl");

        // Act
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        CorsConfiguration config = source.getCorsConfiguration(request);

        // Assert
        assertNotNull(config);
        assertEquals(List.of("https://kampus.pk.edu.pl", "https://app.kampus.pk.edu.pl"), config.getAllowedOrigins());
    }

    @Test
    void shouldReturnAuthenticationManagerFromConfig() throws Exception {
        // Arrange
        AuthenticationConfiguration authConfig = mock(AuthenticationConfiguration.class);
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authConfig.getAuthenticationManager()).thenReturn(authManager);

        // Act
        AuthenticationManager result = securityConfig.authenticationManager(authConfig);

        // Assert
        assertSame(authManager, result);
    }
}

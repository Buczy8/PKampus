package pl.edu.pk.pkampus.modules.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User entity UserDetails unit tests")
class UserTest {

    @Test
    @DisplayName("getAuthorities returns ROLE_<role> when role is set")
    void authoritiesContainRole() {
        // Arrange
        User user = User.builder()
                .role(UserRole.RESIDENT)
                .build();

        // Act
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        // Assert
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_RESIDENT");
    }

    @Test
    @DisplayName("getAuthorities returns empty collection when role is null")
    void authoritiesEmptyWhenRoleNull() {
        // Arrange
        User user = User.builder().role(null).build();

        // Act
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        // Assert
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("isAccountNonLocked returns false when BLOCKED, true otherwise")
    void accountNonLockedChecksStatus() {
        // Arrange & Act & Assert
        User blocked = User.builder().status(UserStatus.BLOCKED).build();
        User active = User.builder().status(UserStatus.ACTIVE).build();
        User pending = User.builder().status(UserStatus.PENDING_APPROVAL).build();

        assertThat(blocked.isAccountNonLocked()).isFalse();
        assertThat(active.isAccountNonLocked()).isTrue();
        assertThat(pending.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isEnabled returns true only when status is ACTIVE")
    void isEnabledOnlyWhenActive() {
        // Arrange & Act & Assert
        User active = User.builder().status(UserStatus.ACTIVE).build();
        User blocked = User.builder().status(UserStatus.BLOCKED).build();
        User checkedOut = User.builder().status(UserStatus.CHECKED_OUT).build();

        assertThat(active.isEnabled()).isTrue();
        assertThat(blocked.isEnabled()).isFalse();
        assertThat(checkedOut.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("getUsername returns email and getPassword returns passwordHash")
    void usernameAndPasswordMatchFields() {
        // Arrange
        User user = User.builder()
                .email("test@pk.edu.pl")
                .passwordHash("hashed_pw")
                .build();

        // Act & Assert
        assertThat(user.getUsername()).isEqualTo("test@pk.edu.pl");
        assertThat(user.getPassword()).isEqualTo("hashed_pw");
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }
}

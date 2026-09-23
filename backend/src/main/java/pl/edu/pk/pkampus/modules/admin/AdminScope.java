package pl.edu.pk.pkampus.modules.admin;

import org.springframework.security.access.AccessDeniedException;
import pl.edu.pk.pkampus.common.exception.BusinessRuleException;
import pl.edu.pk.pkampus.modules.dormitory.Dormitory;
import pl.edu.pk.pkampus.modules.user.User;
import pl.edu.pk.pkampus.modules.user.UserRole;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Shared scope guard for dormitory administrators.
 * Replaces the duplicated {@code requireDormAdminDormitoryId} / {@code requireAdminDormitoryId}
 * guards previously copied into every admin service.
 */
public final class AdminScope {

    private AdminScope() {
    }

    public static Dormitory requireDormitory(User admin, String resource) {
        if (admin.getRole() != UserRole.DORM_ADMIN) {
            throw new AccessDeniedException("Only dormitory administrators can manage " + resource);
        }
        if (admin.getDormitory() == null) {
            throw new BusinessRuleException("Administrator account has no dormitory assigned");
        }
        return admin.getDormitory();
    }

    public static UUID requireDormitoryId(User admin, String resource) {
        return requireDormitory(admin, resource).getId();
    }

    /**
     * Current calendar date derived from the injected clock, in the server's default zone.
     * Equivalent to {@link LocalDate#now()} for a system clock, but deterministic in tests.
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}

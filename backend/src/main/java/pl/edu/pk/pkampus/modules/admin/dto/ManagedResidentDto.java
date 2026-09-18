package pl.edu.pk.pkampus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pl.edu.pk.pkampus.modules.user.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedResidentDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String roomNumber;
    private UserStatus status;
    private String avatarUrl;
    private Instant createdAt;
    private ActiveRoomBanDto activeRoomBan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveRoomBanDto {
        private UUID id;
        private LocalDate startDate;
        private LocalDate endDate;
        private String reason;
    }
}

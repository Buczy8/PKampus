package pl.edu.pk.pkampus.modules.superadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperAdminDormitoryDto {

    private UUID id;
    private String name;
    private String code;
    private String address;
    private Integer floorsCount;
    private LocalTime laundryOpeningTime;
    private LocalTime laundryClosingTime;
    private Integer laundrySlotDurationMinutes;
    private Instant createdAt;
}

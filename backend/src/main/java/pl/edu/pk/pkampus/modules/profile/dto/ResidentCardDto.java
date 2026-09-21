package pl.edu.pk.pkampus.modules.profile.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ResidentCardDto {
    String firstName;
    String lastName;
    String dormitoryName;
    String roomNumber;
    String academicYear;
    String avatarUrl;
    String status;
    String dayCode;
    String dayColorHex;
    String dayColorName;
    Instant serverTime;
}

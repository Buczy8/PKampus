package pl.edu.pk.pkampus.modules.profile.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class CardDayDto {
    String dayCode;
    String dayColorHex;
    String dayColorName;
    String validDate;
    Instant serverTime;
}

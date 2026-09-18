package pl.edu.pk.pkampus.modules.superadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDormitoryRequestDto {

    @Size(max = 10)
    private String code;

    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String address;

    @Min(1)
    @Max(50)
    private Integer floorsCount;

    private LocalTime laundryOpeningTime;

    private LocalTime laundryClosingTime;

    @Min(30)
    @Max(480)
    private Integer laundrySlotDurationMinutes;
}

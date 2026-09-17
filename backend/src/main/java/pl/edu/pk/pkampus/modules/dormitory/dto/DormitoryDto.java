package pl.edu.pk.pkampus.modules.dormitory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DormitoryDto {

    private UUID id;
    private String name;
    private String code;
    private String address;
    private Integer floorsCount;
}

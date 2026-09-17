package pl.edu.pk.pkampus.modules.dormitory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.edu.pk.pkampus.modules.dormitory.dto.DormitoryDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DormitoryService {

    private final DormitoryRepository dormitoryRepository;

    @Transactional(readOnly = true)
    public List<DormitoryDto> getAllDormitories() {
        return dormitoryRepository.findAll().stream()
                .map(d -> DormitoryDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .address(d.getAddress())
                        .floorsCount(d.getFloorsCount())
                        .build())
                .toList();
    }
}

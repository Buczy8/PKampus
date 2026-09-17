package pl.edu.pk.pkampus.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.pk.pkampus.common.ApiResponse;
import pl.edu.pk.pkampus.dto.DormitoryDto;
import pl.edu.pk.pkampus.repository.DormitoryRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dormitories")
@RequiredArgsConstructor
@Tag(name = "Dormitories", description = "Zarządzanie i katalog domów studenckich")
public class DormitoryController {

    private final DormitoryRepository dormitoryRepository;

    @GetMapping
    @Operation(summary = "Pobranie listy akademików", description = "Zwraca listę domów studenckich dostępnych w systemie.")
    public ResponseEntity<ApiResponse<List<DormitoryDto>>> getDormitories() {
        List<DormitoryDto> dtos = dormitoryRepository.findAll().stream()
                .map(d -> DormitoryDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .address(d.getAddress())
                        .floorsCount(d.getFloorsCount())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }
}

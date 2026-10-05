package pl.edu.pk.pkampus.modules.dormitory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.edu.pk.pkampus.modules.dormitory.dto.DormitoryDto;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DormitoryService unit tests (AAA)")
class DormitoryServiceTest {

    @Mock
    private DormitoryRepository dormitoryRepository;

    @InjectMocks
    private DormitoryService dormitoryService;

    private Dormitory dorm1;
    private Dormitory dorm2;

    @BeforeEach
    void setUp() {
        dorm1 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-1")
                .code("DS1")
                .address("ul. Skarżyńskiego 1")
                .floorsCount(4)
                .build();

        dorm2 = Dormitory.builder()
                .id(UUID.randomUUID())
                .name("DS-2")
                .code("DS2")
                .address("ul. Skarżyńskiego 3")
                .floorsCount(5)
                .build();
    }

    @Test
    @DisplayName("Should return mapped list of all dormitories")
    void getAllDormitoriesSuccess() {
        // Arrange
        when(dormitoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dorm1, dorm2));

        // Act
        List<DormitoryDto> result = dormitoryService.getAllDormitories();

        // Assert
        assertEquals(2, result.size());

        DormitoryDto dto1 = result.get(0);
        assertEquals(dorm1.getId(), dto1.getId());
        assertEquals("DS-1", dto1.getName());
        assertEquals("DS1", dto1.getCode());
        assertEquals("ul. Skarżyńskiego 1", dto1.getAddress());
        assertEquals(4, dto1.getFloorsCount());

        DormitoryDto dto2 = result.get(1);
        assertEquals(dorm2.getId(), dto2.getId());
        assertEquals("DS-2", dto2.getName());
        assertEquals("DS2", dto2.getCode());

        verify(dormitoryRepository).findAllByOrderByNameAsc();
    }

    @Test
    @DisplayName("Should return empty list when no dormitories present")
    void getAllDormitoriesEmpty() {
        // Arrange
        when(dormitoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        // Act
        List<DormitoryDto> result = dormitoryService.getAllDormitories();

        // Assert
        assertTrue(result.isEmpty());
        verify(dormitoryRepository).findAllByOrderByNameAsc();
    }
}

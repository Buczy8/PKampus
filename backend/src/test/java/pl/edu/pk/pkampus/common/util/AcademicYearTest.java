package pl.edu.pk.pkampus.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicYearTest {

    @Test
    void shouldUseSeptemberAsAcademicYearStart() {
        assertEquals("2025/2026", AcademicYear.current(LocalDate.of(2025, 9, 1)));
        assertEquals("2025/2026", AcademicYear.current(LocalDate.of(2026, 2, 15)));
        assertEquals("2024/2025", AcademicYear.current(LocalDate.of(2025, 8, 31)));
    }
}

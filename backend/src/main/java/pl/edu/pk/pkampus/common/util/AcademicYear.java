package pl.edu.pk.pkampus.common.util;

import java.time.LocalDate;

public final class AcademicYear {

    private AcademicYear() {
    }

    /**
     * Academic year label in form {@code YYYY/YYYY+1}. Year starts in September.
     */
    public static String current(LocalDate date) {
        int startYear = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    public static String current() {
        return current(LocalDate.now());
    }
}

package com.pareidolia.roster_service.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class WeekUtil {

    private WeekUtil() {}

    /**
     * Week starts on MONDAY
     */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * Week ends on SUNDAY
     */
    public static LocalDate weekEnd(LocalDate date) {
        return date.with(DayOfWeek.SUNDAY);
    }

    public static boolean isSameWeek(
            LocalDate d1,
            LocalDate d2
    ) {
        return weekStart(d1).equals(weekStart(d2));
    }
}

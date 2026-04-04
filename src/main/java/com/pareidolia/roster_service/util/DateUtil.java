package com.pareidolia.roster_service.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class DateUtil {

    private DateUtil() {}

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static LocalDate previousDay(LocalDate date) {
        return date.minusDays(1);
    }

    public static LocalDate nextDay(LocalDate date) {
        return date.plusDays(1);
    }

    public static boolean isSameWeek(
            LocalDate date1,
            LocalDate date2
    ) {
        return WeekUtil.weekStart(date1)
                .equals(WeekUtil.weekStart(date2));
    }
}

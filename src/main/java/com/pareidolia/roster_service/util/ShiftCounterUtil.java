package com.pareidolia.roster_service.util;

import com.pareidolia.roster_service.enumtype.ShiftCode;

import java.util.List;

public final class ShiftCounterUtil {

    private ShiftCounterUtil() {}

    /**
     * Count consecutive occurrences of a shift
     * starting from most recent day
     */
    public static int countConsecutive(
            List<ShiftCode> recentShifts,
            ShiftCode target
    ) {
        int count = 0;

        for (ShiftCode code : recentShifts) {
            if (code == target) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * ✅ NEW: Count consecutive NIGHT + GRAVEYARD together
     * because both are treated as night-category shifts
     */
    public static int countConsecutiveNightCategory(List<ShiftCode> recentShifts) {

        int count = 0;

        for (ShiftCode code : recentShifts) {

            if (code == ShiftCode.NIGHT || code == ShiftCode.GRAVEYARD) {
                count++;
            } else {
                break;
            }
        }

        return count;
    }

    /**
     * Check if last shift was any of given types
     */
    public static boolean lastShiftIs(
            List<ShiftCode> recentShifts,
            ShiftCode... targets
    ) {
        if (recentShifts == null || recentShifts.isEmpty()) {
            return false;
        }

        ShiftCode last = recentShifts.get(0);

        for (ShiftCode code : targets) {
            if (last == code) return true;
        }

        return false;
    }
}

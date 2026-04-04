package com.pareidolia.roster_service.service.context;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import lombok.*;

import java.util.List;


@Getter
@Builder(toBuilder = true)
public class RosterContext {

    private Employee employee;
    private RosterDay rosterDay;
    private ShiftType shiftType;

    private boolean isWeeklyOff;
    private boolean draggedOverride;

    private int weeklyAssignedHours;
    private int consecutiveNightCount;

    private int actualHours;

    private List<ShiftCode> previousShiftCodes;
    private boolean onLeavePreviousDay;

    public ShiftCode getShiftCode() {
        return shiftType.getCode();
    }

    public boolean isAssigningShift(ShiftCode code) {
        return shiftType.getCode() == code;
    }

    public boolean hadPreviousShift(ShiftCode... codes) {
        if (previousShiftCodes == null || previousShiftCodes.isEmpty())
            return false;

        ShiftCode last = previousShiftCodes.get(0);

        for (ShiftCode c : codes) {
            if (last == c) return true;
        }
        return false;
    }
}

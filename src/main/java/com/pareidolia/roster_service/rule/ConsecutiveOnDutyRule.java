package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class ConsecutiveOnDutyRule implements RosterRule{
    @Override
    public void validate(RosterContext context) {

        if (!context.isAssigningShift(ShiftCode.ON_DUTY)) {
            return;
        }

        // 🚨 SAFETY ONLY — prevent 2 in a row
        // but allow planner to manage weekly distribution

        if (context.hadPreviousShift(ShiftCode.ON_DUTY)
                && !context.isDraggedOverride()) {

            throw new BusinessRuleException(
                    "Back-to-back On-Duty not allowed");
        }
    }
}

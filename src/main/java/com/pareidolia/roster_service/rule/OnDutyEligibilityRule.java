package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class OnDutyEligibilityRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        // apply only for ON_DUTY
        if (!context.isAssigningShift(ShiftCode.ON_DUTY)) {
            return;
        }

        // ✅ NEW RULE — anyone below max hours is eligible

        if (context.getWeeklyAssignedHours()
                >= context.getEmployee().getMaxWeeklyHours()) {

            throw new BusinessRuleException(
                    "On-Duty not allowed after completing max weekly hours"
            );
        }

        // safety guard
        if (context.isWeeklyOff()) {
            throw new BusinessRuleException(
                    "On-Duty not allowed on Weekly Off"
            );
        }
    }
}
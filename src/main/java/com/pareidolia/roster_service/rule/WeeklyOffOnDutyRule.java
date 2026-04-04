package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class WeeklyOffOnDutyRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        if (context.isWeeklyOff()
                && context.isAssigningShift(ShiftCode.ON_DUTY)) {

            throw new BusinessRuleException(
                    "On-Duty not allowed on Weekly Off"
            );
        }
    }
}

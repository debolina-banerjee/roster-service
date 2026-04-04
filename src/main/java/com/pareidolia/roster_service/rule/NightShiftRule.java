package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class NightShiftRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        if (!context.isAssigningShift(ShiftCode.EARLY_MORNING)) {
            return;
        }

        if (context.hadPreviousShift(ShiftCode.NIGHT, ShiftCode.GRAVEYARD)) {
            throw new BusinessRuleException(
                    "Early Morning shift not allowed after Night or Graveyard"
            );
        }
    }
}
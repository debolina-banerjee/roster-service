package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.Gender;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class PostLeaveAssignmentRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        if (!context.isOnLeavePreviousDay()) {
            return;
        }

        if (context.isAssigningShift(ShiftCode.EARLY_MORNING)) {
            throw new BusinessRuleException(
                    "Early Morning shift not allowed after leave"
            );
        }

        if (context.getEmployee().getGender() == Gender.FEMALE
                && !context.isAssigningShift(ShiftCode.EVENING)) {

            throw new BusinessRuleException(
                    "Female employees can work only Evening shift after leave"
            );
        }
    }
}

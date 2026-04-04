package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.Gender;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class ConsecutiveNightRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        // Only for MALE
        if (context.getEmployee().getGender() != Gender.MALE)
            return;

        int nights = context.getConsecutiveNightCount();

        // ✅ ONLY block further NIGHT or GRAVEYARD
        if (nights >= 6 &&
                (context.isAssigningShift(ShiftCode.NIGHT)
                        || context.isAssigningShift(ShiftCode.GRAVEYARD))) {

            // 🚨 allow override during recovery
            if (context.isDraggedOverride()) {
                return;
            }

            throw new BusinessRuleException(
                    "Max 6 consecutive night/graveyard reached"
            );
        }
    }
}


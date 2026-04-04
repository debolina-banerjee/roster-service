package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class MaxHoursRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        // ⭐ CRITICAL — allow dragged recovery assignments
        if (context.isDraggedOverride()) {
            return;
        }

        int projectedHours =
                context.getWeeklyAssignedHours()
                        + context.getActualHours();

        if (projectedHours > context.getEmployee().getMaxWeeklyHours()) {
            throw new BusinessRuleException(
                    "Weekly maximum hours exceeded"
            );
        }
    }
}

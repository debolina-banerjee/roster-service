package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class WeeklyOffRule
        implements RosterRule, HardRule {

    @Override
    public void validate(RosterContext context) {

        if (context.isWeeklyOff()) {
            throw new BusinessRuleException(
                    "No shift allowed on weekly off"
            );
        }
    }
}

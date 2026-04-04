package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.rule.EveningToNightDragRule;
import com.pareidolia.roster_service.rule.HardRule;
import com.pareidolia.roster_service.rule.RosterRule;
import com.pareidolia.roster_service.service.context.RosterContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private final List<RosterRule> rules;

    // ✅ SOFT PASS
    public void validate(RosterContext context) {

        for (RosterRule rule : rules) {

            // Skip only HARD rules in soft pass
            if (rule instanceof HardRule) {
                continue;
            }

            rule.validate(context);
        }
    }

    // ✅ HARD PASS (used in recovery)
    public void validateHard(RosterContext context) {

        if (context.isWeeklyOff()&& !context.isDraggedOverride()) {
            throw new BusinessRuleException(
                    "Employee is on Weekly Off – assignment not allowed"
            );
        }

        for (RosterRule rule : rules) {

            // ONLY skip the drag soft rule
            if (rule instanceof EveningToNightDragRule) {
                continue;
            }

            rule.validate(context);
        }
    }
}
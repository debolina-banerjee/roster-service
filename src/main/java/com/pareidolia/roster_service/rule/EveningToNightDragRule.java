package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.rule.RosterRule;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class EveningToNightDragRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        // ✅ If drag override → ALWAYS allow
        if (context.isDraggedOverride()) {
            return;
        }

        // ❗ IMPORTANT:
        // Business confirmed Evening → Night is allowed.
        // So we DO NOT block it anymore.

        // (rule intentionally left permissive)
    }
}
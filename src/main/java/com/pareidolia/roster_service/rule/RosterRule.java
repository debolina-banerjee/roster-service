package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.service.context.RosterContext;

public interface RosterRule {
    void validate(RosterContext context);
}
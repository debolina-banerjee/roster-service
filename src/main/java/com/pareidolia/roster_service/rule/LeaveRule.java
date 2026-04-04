package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.LeaveImportRepository;
import com.pareidolia.roster_service.service.context.RosterContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaveRule implements RosterRule, HardRule {

    private final LeaveImportRepository leaveImportRepository;

    @Override
    public void validate(RosterContext context) {

        boolean onLeave =
                leaveImportRepository
                        .findByEmployee_IdAndLeaveDate(
                                context.getEmployee().getId(),
                                context.getRosterDay().getDayDate()
                        ).isPresent();

        if (onLeave) {
            throw new BusinessRuleException("Employee on leave");
        }
    }
}

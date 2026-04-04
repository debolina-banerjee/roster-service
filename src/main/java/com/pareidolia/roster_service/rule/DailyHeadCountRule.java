package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import com.pareidolia.roster_service.service.context.RosterContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyHeadCountRule implements RosterRule {

    private final ShiftAssignmentRepository repo;
    private final ShiftConfigRepository configRepo;

    @Override
    public void validate(RosterContext c) {

        // ✅ skip ON_DUTY
        if (c.getShiftCode() == ShiftCode.ON_DUTY)
            return;



        Long weekId = c.getRosterDay().getRosterWeek().getId();
        ShiftCode shiftCode = c.getShiftCode();

        int allowed =
                configRepo
                        .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                weekId,
                                c.getRosterDay().getDayCategory(),
                                shiftCode
                        )
                        .map(ShiftConfig::getRequiredResources)
                        .orElse(0);

        long alreadyAssigned =
                repo.countByRosterDayAndShiftCode(
                        c.getRosterDay().getId(),
                        shiftCode
                );

        if (alreadyAssigned >= allowed) {
            throw new BusinessRuleException(
                    "Headcount full for shift: " + shiftCode
            );
        }
    }
}

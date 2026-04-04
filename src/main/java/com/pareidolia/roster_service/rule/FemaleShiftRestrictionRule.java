package com.pareidolia.roster_service.rule;

import com.pareidolia.roster_service.enumtype.Gender;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.service.context.RosterContext;
import org.springframework.stereotype.Component;

@Component
public class FemaleShiftRestrictionRule implements RosterRule {

    @Override
    public void validate(RosterContext context) {

        if (context.getEmployee().getGender() != Gender.FEMALE)
            return;

        ShiftCode code = context.getShiftCode();

        if (code == ShiftCode.NIGHT || code == ShiftCode.GRAVEYARD) {
            throw new BusinessRuleException(
                    "Female employees cannot be assigned Night or Graveyard"
            );
        }
    }
}

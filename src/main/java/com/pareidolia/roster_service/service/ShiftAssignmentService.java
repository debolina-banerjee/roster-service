package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import com.pareidolia.roster_service.service.context.RosterContext;
import com.pareidolia.roster_service.service.context.RosterContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShiftAssignmentService {

    private final ShiftAssignmentRepository repository;
    private final ShiftConfigRepository shiftConfigRepository;
    private final RosterContextBuilder contextBuilder;
    private final ValidationService validationService;

    // =====================================================
    // ✅ MAIN ASSIGN (WITH EVENING HARD CEILING)
    // =====================================================
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assign(RosterContext context) {

        Long empId = context.getEmployee().getId();
        Long dayId = context.getRosterDay().getId();
        ShiftCode code = context.getShiftType().getCode();

        // 🔴 HARD EVENING CEILING (SAFE VERSION)
        if (code == ShiftCode.EVENING) {

            int required =
                    shiftConfigRepository
                            .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                    context.getRosterDay().getRosterWeek().getId(),
                                    context.getRosterDay().getDayCategory(),
                                    ShiftCode.EVENING
                            )
                            .map(ShiftConfig::getRequiredResources)
                            .orElse(0);

            long current =
                    repository.countByRosterDayAndShiftCode(
                            dayId,
                            ShiftCode.EVENING
                    );

            if ( current >= required) {
                throw new BusinessRuleException(
                        "Evening capacity reached — blocking overflow"
                );
            }
        }

        if (code == ShiftCode.GRAVEYARD) {

            int required =
                    shiftConfigRepository
                            .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                    context.getRosterDay().getRosterWeek().getId(),
                                    context.getRosterDay().getDayCategory(),
                                    ShiftCode.GRAVEYARD
                            )
                            .map(ShiftConfig::getRequiredResources)
                            .orElse(0);

            long current =
                    repository.countByRosterDayAndShiftCode(
                            dayId,
                            ShiftCode.GRAVEYARD
                    );

            if ( current >= required) {
                throw new BusinessRuleException(
                        "Graveyard capacity reached — blocking overflow"
                );
            }
        }


        // =====================================================
        // ✅ SAFE REPLACE (idempotent)
        // =====================================================
        repository
                .findByEmployee_IdAndRosterDay_Id(empId, dayId)
                .ifPresent(repository::delete);

        ShiftAssignment assignment =
                ShiftAssignment.builder()
                        .employee(context.getEmployee())
                        .rosterDay(context.getRosterDay())
                        .shiftType(context.getShiftType())
                        .dragged(context.isDraggedOverride())
                        .onDuty(context.getShiftType().isOnDuty())
                        .actualHours(context.getActualHours())
                        .build();

        repository.save(assignment);
    }

    // =====================================================
    // ✅ DRAGGED ASSIGN (USED BY RECOVERY)
    // =====================================================
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignDragged(
            Employee employee,
            RosterDay day,
            ShiftType shiftType) {

        RosterContext base =
                contextBuilder.build(employee, day, shiftType);

        RosterContext ctx =
                base.toBuilder()
                        .draggedOverride(true)
                        .build();

        validationService.validateHard(ctx);

        assign(ctx);
    }

}
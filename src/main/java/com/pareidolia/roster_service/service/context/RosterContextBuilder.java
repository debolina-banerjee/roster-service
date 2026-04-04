package com.pareidolia.roster_service.service.context;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.entity.RosterDay;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.LeaveImportRepository;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.WeeklyOffRepository;
import com.pareidolia.roster_service.util.ShiftCounterUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RosterContextBuilder {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final LeaveImportRepository leaveImportRepository;
    private final WeeklyOffRepository weeklyOffRepository;

    public RosterContextBuilder(
            ShiftAssignmentRepository shiftAssignmentRepository,
            LeaveImportRepository leaveImportRepository,
            WeeklyOffRepository weeklyOffRepository
    ) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.leaveImportRepository = leaveImportRepository;
        this.weeklyOffRepository = weeklyOffRepository;
    }

    public RosterContext build(
            Employee employee,
            RosterDay rosterDay,
            ShiftType shiftType
    ) {

        LocalDate date = rosterDay.getDayDate();

        // Weekly Off Check
        boolean weeklyOff =
                weeklyOffRepository.existsByEmployee_IdAndOffDate(
                        employee.getId(),
                        date
                );

        // 🔥 FIXED: Only shifts BEFORE current date
        List<ShiftCode> previousShiftCodes =
                shiftAssignmentRepository.findRecentShiftCodesBeforeDate(
                        employee.getId(),
                        date,
                        PageRequest.of(0, 5)
                );

        // Leave Check
        boolean wasOnLeavePreviousDay =
                leaveImportRepository
                        .findByEmployee_IdAndLeaveDate(
                                employee.getId(),
                                date.minusDays(1)
                        )
                        .isPresent();

        // Weekly Hours Sum
        int weeklyHours =
                shiftAssignmentRepository.sumWeeklyHours(
                        employee.getId(),
                        rosterDay.getRosterWeek().getId()
                ).intValue();

        // Consecutive NIGHT + GRAVEYARD
        int consecutiveNights =
                ShiftCounterUtil.countConsecutiveNightCategory(previousShiftCodes);

        return RosterContext.builder()
                .employee(employee)
                .rosterDay(rosterDay)
                .shiftType(shiftType)
                .isWeeklyOff(weeklyOff)
                .previousShiftCodes(previousShiftCodes)
                .onLeavePreviousDay(wasOnLeavePreviousDay)
                .weeklyAssignedHours(weeklyHours)
                .consecutiveNightCount(consecutiveNights)
                .actualHours(shiftType.getShiftHours())
                .draggedOverride(false)
                .build();
    }
}

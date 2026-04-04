package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.EmployeeLevel;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.*;
import com.pareidolia.roster_service.service.context.RosterContext;
import com.pareidolia.roster_service.service.context.RosterContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnDutyTopupService {

    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final RosterWeekRepository rosterWeekRepository;
    private final RosterDayRepository rosterDayRepository;
    private final LeaveImportRepository leaveImportRepository;
    private final WeeklyOffRepository weeklyOffRepository;
    private final ValidationService validationService;
    private final RosterContextBuilder contextBuilder;
    private final ShiftAssignmentService assignmentService;

    @Transactional
    public void fillOnDuty(LocalDate weekStart) {

        ShiftType onDuty =
                shiftTypeRepository.findByCode(ShiftCode.ON_DUTY)
                        .orElseThrow();

        int onDutyHours = onDuty.getShiftHours();

        RosterWeek week =
                rosterWeekRepository.findByWeekStartDate(weekStart)
                        .orElseThrow();

        Long weekId = week.getId();

        List<Employee> employees =
                employeeRepository.findByActiveTrue();

        // =====================================================
        // 🔥 SEEDED FAIR ROTATION (prevents same people always topping up)
        // =====================================================
        Collections.shuffle(employees, new Random(weekId));

        // keep your senior priority AFTER shuffle
        employees.sort(Comparator.comparingInt(e ->
                e.getEmployeeLevel() == EmployeeLevel.SENIOR ? 1 :
                        e.getEmployeeLevel() == EmployeeLevel.MID_SENIOR ? 2 : 3
        ));

        // =====================================================
        // 🔥 SORT DAYS (CRITICAL FIX)
        // =====================================================
        List<RosterDay> days =
                rosterDayRepository.findByRosterWeekId(weekId);

        days.sort(Comparator.comparing(RosterDay::getDayDate));

        // =====================================================
        // 🔥 MAIN TOP-UP LOOP
        // =====================================================
        for (Employee emp : employees) {

            int maxHours = emp.getMaxWeeklyHours();

            int currentHours =
                    shiftAssignmentRepository
                            .sumWeeklyHours(emp.getId(), weekId)
                            .intValue();

            if (currentHours >= maxHours) {
                continue;
            }

            log.debug("Top-up start → emp={} current={} target={}",
                    emp.getEmployeeCode(), currentHours, maxHours);

            for (RosterDay day : days) {

                // recompute every iteration
                currentHours =
                        shiftAssignmentRepository
                                .sumWeeklyHours(emp.getId(), weekId)
                                .intValue();

                int remaining = maxHours - currentHours;

                if (remaining <= 0) {
                    break;
                }

                // skip if already assigned
                if (shiftAssignmentRepository
                        .existsByEmployee_IdAndRosterDay_Id(
                                emp.getId(), day.getId()))
                    continue;

                // skip leave
                if (leaveImportRepository
                        .findByEmployee_IdAndLeaveDate(
                                emp.getId(), day.getDayDate())
                        .isPresent())
                    continue;

                // skip weekly off
                if (weeklyOffRepository
                        .existsByEmployee_IdAndOffDate(
                                emp.getId(), day.getDayDate()))
                    continue;

                int hoursToAssign = Math.min(onDutyHours, remaining);

                RosterContext ctx =
                        contextBuilder.build(emp, day, onDuty)
                                .toBuilder()
                                .actualHours(hoursToAssign)
                                .draggedOverride(true)
                                .build();

                try {
                    validationService.validateHard(ctx);
                    assignmentService.assign(ctx);

                    log.debug("ON_DUTY assigned → emp={} hours={} remainingAfter={}",
                            emp.getEmployeeCode(),
                            hoursToAssign,
                            (remaining - hoursToAssign));

                } catch (BusinessRuleException ex) {
                    log.debug("ON_DUTY blocked → emp={} reason={}",
                            emp.getEmployeeCode(),
                            ex.getMessage());
                }
            }

            int finalHours =
                    shiftAssignmentRepository
                            .sumWeeklyHours(emp.getId(), weekId)
                            .intValue();

            if (finalHours < maxHours) {
                log.warn("Employee still underfilled → emp={} hours={}/{}",
                        emp.getEmployeeCode(), finalHours, maxHours);
            }
        }
    }
}
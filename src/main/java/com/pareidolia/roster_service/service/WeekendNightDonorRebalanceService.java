package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.entity.RosterDay;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.Gender;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.EmployeeRepository;
import com.pareidolia.roster_service.repository.RosterDayRepository;
import com.pareidolia.roster_service.repository.RosterWeekRepository;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import com.pareidolia.roster_service.repository.ShiftTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeekendNightDonorRebalanceService {

    private final RosterWeekRepository rosterWeekRepository;
    private final RosterDayRepository rosterDayRepository;
    private final ShiftConfigRepository shiftConfigRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentService assignmentService;

    /**
     * Business rule:
     * Existing planner remains untouched.
     * Existing evening drag remains untouched.
     *
     * After planner completes:
     * - Weekend cannot remain short.
     * - Use max 2 donor resources across the week.
     * - Donor concept comes from weekday NIGHT + GRAVEYARD capacity.
     *
     * Option A:
     * We DO NOT delete weekday assignments.
     * We only use approved donor allowance to fill weekend shortages.
     */
    @Transactional
    public void execute(LocalDate weekStartDate) {

        RosterWeek week =
                rosterWeekRepository.findByWeekStartDate(weekStartDate)
                        .orElseThrow(() ->
                                new RuntimeException("Roster week not found"));

        List<RosterDay> weekendDays =
                rosterDayRepository.findByRosterWeekId(week.getId())
                        .stream()
                        .filter(d -> d.getDayCategory() == DayCategory.WEEKEND)
                        .sorted(Comparator.comparing(RosterDay::getDayDate))
                        .toList();

        if (weekendDays.isEmpty()) {
            return;
        }

        int donorPool = 2; // fixed approved manpower buffer

        for (RosterDay day : weekendDays) {

            if (donorPool <= 0) {
                break;
            }

            donorPool = fillWeekendDay(day, donorPool);
        }

        log.info("WeekendNightDonorRebalance complete | week={} | remainingDonors={}",
                weekStartDate, donorPool);
    }

    private int fillWeekendDay(RosterDay day, int donorPool) {

        List<ShiftConfig> configs =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                day.getRosterWeek().getId(),
                                DayCategory.WEEKEND
                        );

        for (ShiftConfig cfg : configs) {

            if (donorPool <= 0) {
                break;
            }

            ShiftCode code = cfg.getShiftType().getCode();

            if (code == ShiftCode.ON_DUTY) {
                continue;
            }

            int required = cfg.getRequiredResources();

            long current =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(),
                            code
                    );

            if (current >= required) {
                continue;
            }

            int shortage = required - (int) current;

            while (shortage > 0 && donorPool > 0) {

                boolean filled = assignOneWeekendResource(day, cfg.getShiftType());

                if (!filled) {
                    break;
                }

                shortage--;
                donorPool--;
            }
        }

        return donorPool;
    }

    private boolean assignOneWeekendResource(
            RosterDay day,
            ShiftType targetShiftType
    ) {

        ShiftCode targetCode = targetShiftType.getCode();

        List<Employee> pool =
                employeeRepository.findActiveNotOnLeave(day.getDayDate())
                        .stream()

                        // must not already be assigned that day
                        .filter(e ->
                                !shiftAssignmentRepository
                                        .existsByEmployee_IdAndRosterDay_Id(
                                                e.getId(),
                                                day.getId()
                                        )
                        )

                        // females blocked from NIGHT / GRAVEYARD
                        .filter(e -> {

                            if (targetCode == ShiftCode.NIGHT ||
                                    targetCode == ShiftCode.GRAVEYARD) {
                                return e.getGender() != Gender.FEMALE;
                            }

                            return true;
                        })

                        // lowest weekly hours first
                        .sorted(Comparator.comparingLong(
                                e -> shiftAssignmentRepository.sumWeeklyHours(
                                        e.getId(),
                                        day.getRosterWeek().getId()
                                )
                        ))
                        .toList();

        for (Employee emp : pool) {

            try {

                assignmentService.assignDragged(
                        emp,
                        day,
                        targetShiftType
                );

                log.warn("Weekend donor used | date={} | emp={} | shift={}",
                        day.getDayDate(),
                        emp.getEmployeeCode(),
                        targetCode);

                return true;

            } catch (BusinessRuleException ignored) {
            }
        }

        return false;
    }
}
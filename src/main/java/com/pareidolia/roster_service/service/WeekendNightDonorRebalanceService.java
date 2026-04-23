package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.entity.RosterDay;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.EmployeeRepository;
import com.pareidolia.roster_service.repository.RosterDayRepository;
import com.pareidolia.roster_service.repository.RosterWeekRepository;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeekendNightDonorRebalanceService {

    private final RosterWeekRepository rosterWeekRepository;
    private final RosterDayRepository rosterDayRepository;
    private final ShiftConfigRepository shiftConfigRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftAssignmentService assignmentService;
    private final EmployeeRepository employeeRepository;

    // seniors said max two weekday donor resources/week
    private static final int MAX_WEEKLY_DONOR_MOVES = 2;

    // weekday night-family must remain at least 7
    private static final int MIN_WEEKDAY_NIGHT_FAMILY_AFTER_MOVE = 7;

    @Transactional
    public void execute(LocalDate weekStartDate) {

        RosterWeek week =
                rosterWeekRepository.findByWeekStartDate(weekStartDate)
                        .orElseThrow();

        List<RosterDay> allDays =
                rosterDayRepository.findByRosterWeekId(week.getId());

        List<RosterDay> weekdays =
                allDays.stream()
                        .filter(d -> d.getDayCategory() == DayCategory.WEEKDAY)
                        .sorted(Comparator.comparing(RosterDay::getDayDate))
                        .collect(Collectors.toList());

        List<RosterDay> weekends =
                allDays.stream()
                        .filter(d -> d.getDayCategory() == DayCategory.WEEKEND)
                        .sorted(Comparator.comparing(RosterDay::getDayDate))
                        .collect(Collectors.toList());

        Set<Long> movedEmployees = new HashSet<>();
        int movesLeft = MAX_WEEKLY_DONOR_MOVES;

        for (RosterDay weekendDay : weekends) {

            if (movesLeft <= 0) break;

            List<ShiftConfig> configs =
                    shiftConfigRepository
                            .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                    week.getId(),
                                    DayCategory.WEEKEND
                            );

            // weekend shortage priority
            configs.sort((a, b) ->
                    Integer.compare(
                            priority(a.getShiftType().getCode()),
                            priority(b.getShiftType().getCode())
                    )
            );

            for (ShiftConfig cfg : configs) {

                if (movesLeft <= 0) break;

                ShiftCode targetCode = cfg.getShiftType().getCode();

                if (targetCode == ShiftCode.ON_DUTY) continue;

                int required = cfg.getRequiredResources();

                long current =
                        shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                weekendDay.getId(),
                                targetCode
                        );

                while (current < required && movesLeft > 0) {

                    boolean moved =
                            moveOneDonor(
                                    weekdays,
                                    weekendDay,
                                    cfg.getShiftType(),
                                    movedEmployees
                            );

                    if (!moved) {
                        break;
                    }

                    movesLeft--;

                    current =
                            shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                    weekendDay.getId(),
                                    targetCode
                            );
                }
            }
        }

        log.info("WeekendNightDonorRebalance completed");
    }

    private boolean moveOneDonor(
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftType targetShift,
            Set<Long> movedEmployees
    ) {

        for (RosterDay weekday : weekdays) {

            // hard threshold: weekday NIGHT + GRAVEYARD must remain >= 7
            long nightCount =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            weekday.getId(),
                            ShiftCode.NIGHT
                    );

            long graveyardCount =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            weekday.getId(),
                            ShiftCode.GRAVEYARD
                    );

            long totalNightFamily = nightCount + graveyardCount;

            if ((totalNightFamily - 1) < MIN_WEEKDAY_NIGHT_FAMILY_AFTER_MOVE) {
                continue;
            }

            Employee donor =
                    findSafeDonor(
                            weekday,
                            ShiftCode.GRAVEYARD,
                            movedEmployees,
                            weekendDay
                    );

            ShiftCode donorCode = ShiftCode.GRAVEYARD;

            if (donor == null) {
                donor =
                        findSafeDonor(
                                weekday,
                                ShiftCode.NIGHT,
                                movedEmployees,
                                weekendDay
                        );
                donorCode = ShiftCode.NIGHT;
            }

            if (donor == null) continue;

            try {

                // duplicate safety
                boolean alreadyWeekendAssigned =
                        shiftAssignmentRepository
                                .existsByEmployee_IdAndRosterDay_Id(
                                        donor.getId(),
                                        weekendDay.getId()
                                );

                if (alreadyWeekendAssigned) {
                    continue;
                }

                Employee managed =
                        employeeRepository.findById(donor.getId())
                                .orElseThrow();

                // remove weekday donor shift first
                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                managed.getId(),
                                weekday.getId(),
                                donorCode
                        );

                shiftAssignmentRepository.flush();

                // assign weekend shortage shift
                assignmentService.assignDragged(
                        managed,
                        weekendDay,
                        targetShift
                );

                movedEmployees.add(managed.getId());

                log.warn(
                        "Moved donor emp={} from {} {} -> {} {}",
                        managed.getEmployeeCode(),
                        weekday.getDayDate(),
                        donorCode,
                        weekendDay.getDayDate(),
                        targetShift.getCode()
                );

                return true;

            } catch (Exception ex) {

                log.error(
                        "Donor move failed emp={} reason={}",
                        donor.getEmployeeCode(),
                        ex.getMessage()
                );
            }
        }

        return false;
    }

    private Employee findSafeDonor(
            RosterDay weekday,
            ShiftCode code,
            Set<Long> movedEmployees,
            RosterDay weekendDay
    ) {

        List<Employee> pool =
                shiftAssignmentRepository
                        .findEmployeesByShiftCodeAndDate(
                                code,
                                weekday.getDayDate()
                        );

        for (Employee emp : pool) {

            if (movedEmployees.contains(emp.getId())) {
                continue;
            }

            boolean alreadyWeekendAssigned =
                    shiftAssignmentRepository
                            .existsByEmployee_IdAndRosterDay_Id(
                                    emp.getId(),
                                    weekendDay.getId()
                            );

            if (alreadyWeekendAssigned) {
                continue;
            }

            return emp;
        }

        return null;
    }

    private int priority(ShiftCode code) {

        return switch (code) {
            case EARLY_MORNING -> 1;
            case EVENING -> 2;
            case NIGHT -> 3;
            case GRAVEYARD -> 4;
            default -> 99;
        };
    }
}
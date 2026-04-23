package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.*;
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

    private static final int MAX_TOTAL_DONORS = 2;
    private static final int MIN_WEEKDAY_NIGHT_FAMILY_AFTER_DONOR = 7;

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

        int donorsUsed = 0;

        Set<LocalDate> donatedWeekdays = new HashSet<>();

        // Priority order based on your recurring shortages
        List<ShiftCode> priority =
                List.of(
                        ShiftCode.EARLY_MORNING,
                        ShiftCode.EVENING
                );

        for (RosterDay weekendDay : weekends) {

            for (ShiftCode targetCode : priority) {

                while (donorsUsed < MAX_TOTAL_DONORS &&
                        hasShortage(week, weekendDay, targetCode)) {

                    boolean moved =
                            moveOneDonor(
                                    week,
                                    weekdays,
                                    weekendDay,
                                    targetCode,
                                    donatedWeekdays
                            );

                    if (!moved) {
                        break;
                    }

                    donorsUsed++;
                }

                if (donorsUsed >= MAX_TOTAL_DONORS) {
                    return;
                }
            }
        }
    }

    private boolean hasShortage(
            RosterWeek week,
            RosterDay weekendDay,
            ShiftCode targetCode
    ) {

        int required =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                week.getId(),
                                DayCategory.WEEKEND,
                                targetCode
                        )
                        .map(ShiftConfig::getRequiredResources)
                        .orElse(0);

        long current =
                shiftAssignmentRepository
                        .countByRosterDayAndShiftCode(
                                weekendDay.getId(),
                                targetCode
                        );

        return current < required;
    }

    private boolean moveOneDonor(
            RosterWeek week,
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftCode targetCode,
            Set<LocalDate> donatedWeekdays
    ) {

        for (RosterDay weekday : weekdays) {

            // one donor max from each weekday
            if (donatedWeekdays.contains(weekday.getDayDate())) {
                continue;
            }

            long nightCount =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            weekday.getId(),
                            ShiftCode.NIGHT
                    );

            long graveCount =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            weekday.getId(),
                            ShiftCode.GRAVEYARD
                    );

            long totalNightFamily = nightCount + graveCount;

            // must remain >= 7 after move
            if (totalNightFamily - 1 < MIN_WEEKDAY_NIGHT_FAMILY_AFTER_DONOR) {
                continue;
            }

            boolean moved =
                    tryMoveFromPool(
                            week,
                            weekday,
                            weekendDay,
                            targetCode,
                            graveCount > 0
                    );

            if (!moved) {
                moved =
                        tryMoveFromPool(
                                week,
                                weekday,
                                weekendDay,
                                targetCode,
                                false
                        );
            }

            if (moved) {
                donatedWeekdays.add(weekday.getDayDate());
                return true;
            }
        }

        return false;
    }

    private boolean tryMoveFromPool(
            RosterWeek week,
            RosterDay weekday,
            RosterDay weekendDay,
            ShiftCode targetCode,
            boolean preferGraveyard
    ) {

        ShiftCode donorCode =
                preferGraveyard
                        ? ShiftCode.GRAVEYARD
                        : ShiftCode.NIGHT;

        List<Employee> donors =
                shiftAssignmentRepository
                        .findEmployeesByShiftCodeAndDate(
                                donorCode,
                                weekday.getDayDate()
                        )
                        .stream()
                        .sorted(Comparator.comparing(Employee::getId))
                        .collect(Collectors.toList());

        for (Employee donor : donors) {

            boolean alreadyAssigned =
                    shiftAssignmentRepository
                            .existsByEmployee_IdAndRosterDay_Id(
                                    donor.getId(),
                                    weekendDay.getId()
                            );

            if (alreadyAssigned) {
                continue;
            }

            try {

                Employee managed =
                        employeeRepository.findById(donor.getId())
                                .orElseThrow();

                ShiftType targetShift =
                        shiftConfigRepository
                                .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                        week.getId(),
                                        DayCategory.WEEKEND,
                                        targetCode
                                )
                                .map(ShiftConfig::getShiftType)
                                .orElseThrow();

                // Assign weekend first
                assignmentService.assignDragged(
                        managed,
                        weekendDay,
                        targetShift
                );

                // Delete weekday donor only after success
                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                managed.getId(),
                                weekday.getId(),
                                donorCode
                        );

                log.info(
                        "DONOR MOVE | {} | {} {} -> {} {}",
                        managed.getEmployeeCode(),
                        weekday.getDayDate(),
                        donorCode,
                        weekendDay.getDayDate(),
                        targetCode
                );

                return true;

            } catch (BusinessRuleException ex) {

                log.warn(
                        "DONOR BLOCKED | emp={} | reason={}",
                        donor.getEmployeeCode(),
                        ex.getMessage()
                );

            } catch (Exception ex) {

                log.error(
                        "DONOR ERROR | emp={}",
                        donor.getEmployeeCode(),
                        ex
                );
            }
        }

        return false;
    }
}
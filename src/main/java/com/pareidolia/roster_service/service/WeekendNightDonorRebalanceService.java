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
                        .toList();

        List<RosterDay> weekends =
                allDays.stream()
                        .filter(d -> d.getDayCategory() == DayCategory.WEEKEND)
                        .sorted(Comparator.comparing(RosterDay::getDayDate))
                        .toList();

        int donorMovesLeft = 2;

        Set<LocalDate> usedWeekdays = new HashSet<>();

        for (RosterDay weekendDay : weekends) {

            donorMovesLeft =
                    fillShortage(
                            week,
                            weekdays,
                            weekendDay,
                            ShiftCode.EARLY_MORNING,
                            donorMovesLeft,
                            usedWeekdays
                    );

            donorMovesLeft =
                    fillShortage(
                            week,
                            weekdays,
                            weekendDay,
                            ShiftCode.EVENING,
                            donorMovesLeft,
                            usedWeekdays
                    );

            if (donorMovesLeft <= 0) {
                return;
            }
        }
    }

    private int fillShortage(
            RosterWeek week,
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftCode targetCode,
            int donorMovesLeft,
            Set<LocalDate> usedWeekdays
    ) {

        if (donorMovesLeft <= 0) return 0;

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

        int shortage = required - (int) current;

        while (shortage > 0 && donorMovesLeft > 0) {

            boolean moved =
                    moveOneDonor(
                            weekdays,
                            weekendDay,
                            targetCode,
                            usedWeekdays
                    );

            if (!moved) break;

            shortage--;
            donorMovesLeft--;
        }

        return donorMovesLeft;
    }

    private boolean moveOneDonor(
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftCode targetCode,
            Set<LocalDate> usedWeekdays
    ) {

        for (RosterDay weekday : weekdays) {

            if (usedWeekdays.contains(weekday.getDayDate())) {
                continue;
            }

            if (tryMoveFromShift(
                    weekday,
                    weekendDay,
                    ShiftCode.GRAVEYARD,
                    5,
                    targetCode
            )) {
                usedWeekdays.add(weekday.getDayDate());
                return true;
            }

            if (tryMoveFromShift(
                    weekday,
                    weekendDay,
                    ShiftCode.NIGHT,
                    1,
                    targetCode
            )) {
                usedWeekdays.add(weekday.getDayDate());
                return true;
            }
        }

        return false;
    }

    private boolean tryMoveFromShift(
            RosterDay weekday,
            RosterDay weekendDay,
            ShiftCode donorCode,
            int minimumAfterMove,
            ShiftCode targetCode
    ) {

        long current =
                shiftAssignmentRepository
                        .countByRosterDayAndShiftCode(
                                weekday.getId(),
                                donorCode
                        );

        if (current <= minimumAfterMove) {
            return false;
        }

        List<Employee> donors =
                shiftAssignmentRepository
                        .findEmployeesByShiftCodeAndDate(
                                donorCode,
                                weekday.getDayDate()
                        )
                        .stream()
                        .sorted(Comparator.comparing(Employee::getId))
                        .toList();

        for (Employee donor : donors) {

            boolean alreadyAssigned =
                    shiftAssignmentRepository
                            .existsByEmployee_IdAndRosterDay_Id(
                                    donor.getId(),
                                    weekendDay.getId()
                            );

            if (alreadyAssigned) continue;

            try {

                Employee managed =
                        employeeRepository.findById(donor.getId())
                                .orElseThrow();

                ShiftType targetShift =
                        shiftConfigRepository
                                .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                        weekendDay.getRosterWeek().getId(),
                                        DayCategory.WEEKEND,
                                        targetCode
                                )
                                .map(ShiftConfig::getShiftType)
                                .orElseThrow();

                assignmentService.assignDragged(
                        managed,
                        weekendDay,
                        targetShift
                );

                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                managed.getId(),
                                weekday.getId(),
                                donorCode
                        );

                log.info("Moved {} from {} {} -> {} {}",
                        managed.getEmployeeCode(),
                        weekday.getDayDate(),
                        donorCode,
                        weekendDay.getDayDate(),
                        targetCode);

                return true;

            } catch (BusinessRuleException ex) {
                log.warn("Blocked donor {}", ex.getMessage());
            } catch (Exception ex) {
                log.error("Move failed", ex);
            }
        }

        return false;
    }
}
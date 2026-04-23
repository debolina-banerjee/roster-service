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

        for (RosterDay weekendDay : weekends) {

            List<ShiftConfig> weekendConfigs =
                    shiftConfigRepository
                            .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                    week.getId(),
                                    DayCategory.WEEKEND
                            );

            for (ShiftConfig cfg : weekendConfigs) {

                if (donorMovesLeft <= 0) {
                    return;
                }

                ShiftCode targetCode = cfg.getShiftType().getCode();

                if (targetCode == ShiftCode.ON_DUTY) {
                    continue;
                }

                int required = cfg.getRequiredResources();

                long current =
                        shiftAssignmentRepository
                                .countByRosterDayAndShiftCode(
                                        weekendDay.getId(),
                                        targetCode
                                );

                if (current >= required) {
                    continue;
                }

                int shortage = required - (int) current;

                while (shortage > 0 && donorMovesLeft > 0) {

                    boolean moved =
                            moveOneDonor(
                                    weekdays,
                                    weekendDay,
                                    cfg.getShiftType()
                            );

                    if (!moved) {
                        break;
                    }

                    shortage--;
                    donorMovesLeft--;
                }
            }
        }

        log.info("Weekend donor rebalance completed");
    }

    private boolean moveOneDonor(
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftType weekendShift
    ) {

        for (RosterDay weekday : weekdays) {

            Employee donor = findDonor(weekday, ShiftCode.GRAVEYARD);
            ShiftCode donorCode = ShiftCode.GRAVEYARD;

            if (donor == null) {
                donor = findDonor(weekday, ShiftCode.NIGHT);
                donorCode = ShiftCode.NIGHT;
            }

            if (donor == null) {
                continue;
            }

            try {

                // 🔥 IMPORTANT: skip if already working on this weekend day
                boolean alreadyAssigned =
                        shiftAssignmentRepository
                                .existsByEmployee_IdAndRosterDay_Id(
                                        donor.getId(),
                                        weekendDay.getId()
                                );

                if (alreadyAssigned) {
                    continue;
                }

                // 🔥 refetch managed entity
                Employee managedDonor =
                        employeeRepository
                                .findById(donor.getId())
                                .orElseThrow();

                // 🔥 assign weekend shift first
                assignmentService.assignDragged(
                        managedDonor,
                        weekendDay,
                        weekendShift
                );

                // 🔥 remove weekday donor shift after success
                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                managedDonor.getId(),
                                weekday.getId(),
                                donorCode
                        );

                log.warn(
                        "Donor moved emp={} from {} {} -> {} {}",
                        managedDonor.getEmployeeCode(),
                        weekday.getDayDate(),
                        donorCode,
                        weekendDay.getDayDate(),
                        weekendShift.getCode()
                );

                return true;

            } catch (BusinessRuleException ex) {
                log.warn("Donor move blocked: {}", ex.getMessage());

            } catch (Exception ex) {
                log.error("Donor move failed", ex);
            }
        }

        return false;
    }

    private Employee findDonor(
            RosterDay day,
            ShiftCode code
    ) {

        return shiftAssignmentRepository
                .findEmployeesByShiftCodeAndDate(
                        code,
                        day.getDayDate()
                )
                .stream()
                .sorted(Comparator.comparingLong(Employee::getId))
                .findFirst()
                .orElse(null);
    }
}
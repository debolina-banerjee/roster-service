package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.Gender;
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

        int maxDonors = 2;

        for (RosterDay weekendDay : weekends) {

            List<ShiftConfig> configs =
                    shiftConfigRepository
                            .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                    week.getId(),
                                    DayCategory.WEEKEND
                            );

            for (ShiftConfig cfg : configs) {

                if (maxDonors <= 0) return;

                ShiftCode targetCode = cfg.getShiftType().getCode();

                if (targetCode == ShiftCode.ON_DUTY) continue;

                int required = cfg.getRequiredResources();

                long current =
                        shiftAssignmentRepository
                                .countByRosterDayAndShiftCode(
                                        weekendDay.getId(),
                                        targetCode
                                );

                if (current >= required) continue;

                int shortage = required - (int) current;

                while (shortage > 0 && maxDonors > 0) {

                    boolean moved =
                            moveOneDonor(
                                    weekdays,
                                    weekendDay,
                                    cfg.getShiftType()
                            );

                    if (!moved) break;

                    shortage--;
                    maxDonors--;
                }
            }
        }

        log.info("Weekend donor rebalance complete");
    }

    private boolean moveOneDonor(
            List<RosterDay> weekdays,
            RosterDay weekendDay,
            ShiftType weekendShift
    ) {

        for (RosterDay weekday : weekdays) {

            Employee donor =
                    findDonor(weekday, ShiftCode.GRAVEYARD);

            ShiftCode donorCode = ShiftCode.GRAVEYARD;

            if (donor == null) {
                donor = findDonor(weekday, ShiftCode.NIGHT);
                donorCode = ShiftCode.NIGHT;
            }

            if (donor == null) continue;

            try {

                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                donor.getId(),
                                weekday.getId(),
                                donorCode
                        );

                shiftAssignmentRepository.flush();

                assignmentService.assignDragged(
                        donor,
                        weekendDay,
                        weekendShift
                );

                log.warn(
                        "Moved donor emp={} from {} {} -> {} {}",
                        donor.getEmployeeCode(),
                        weekday.getDayDate(),
                        donorCode,
                        weekendDay.getDayDate(),
                        weekendShift.getCode()
                );

                return true;

            } catch (BusinessRuleException ex) {
                // rollback candidate only
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
package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RosterGenerationService {

    private final RosterWeekRepository rosterWeekRepository;
    private final RosterDayRepository rosterDayRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    private final WeeklySummaryService summaryService;
    private final WeeklyOffService weeklyOffService;
    private final ShiftPlannerService shiftPlannerService;
    private final OnDutyTopupService onDutyTopupService;
    private final WeekendNightDonorRebalanceService weekendNightDonorRebalanceService;

    public void generateForWeek(LocalDate weekStartDate) {

        // ✅ fetch week first (FIX)
        RosterWeek rosterWeek =
                rosterWeekRepository.findByWeekStartDate(weekStartDate)
                        .orElseThrow();

        // 1. Generate weekly offs
        weeklyOffService.generateWeeklyOffs( rosterWeek.getId(),weekStartDate);

//        List<RosterDay> days =
//                rosterDayRepository.findByRosterWeekId(rosterWeek.getId());
//
//        // 2. Plan normal shifts
//        for (RosterDay day : days) {
//            shiftPlannerService.planDay(day);
//        }

        List<RosterDay> days =
                rosterDayRepository.findByRosterWeekId(rosterWeek.getId());

//        days = days.stream()
//                .sorted((a, b) -> Integer.compare(
//                        planningPriority(a.getDayDate().getDayOfWeek().getValue()),
//                        planningPriority(b.getDayDate().getDayOfWeek().getValue())
//                ))
//                .toList();

        days = days.stream()
                .sorted((a, b) -> Integer.compare(
                        planningPriority(a.getDayDate().getDayOfWeek().getValue()),
                        planningPriority(b.getDayDate().getDayOfWeek().getValue())
                ))
                .collect(java.util.stream.Collectors.toList());

// DEBUG ORDER CHECK
        for (RosterDay d : days) {
            System.out.println("PLAN ORDER => " + d.getDayDate());
        }

// 2. Plan shifts (WEEKEND FIRST)
        for (RosterDay day : days) {
            shiftPlannerService.planDay(day);
        }

        // 3. ON_DUTY topup
        onDutyTopupService.fillOnDuty(weekStartDate);


        weekendNightDonorRebalanceService.execute(weekStartDate);

        // 4. FAIRNESS + HOURS SUMMARY (UNCHANGED ✅)
        summaryService.calculateSummary(
                rosterWeek.getId(),
                weekStartDate
        );
    }

    public List<ShiftAssignment> getAssignmentsForDay(LocalDate date) {
        return shiftAssignmentRepository.findByRosterDay_DayDate(date);
    }

    public List<ShiftAssignment> getAssignmentsForWeek(Long weekId) {
        return shiftAssignmentRepository.findByRosterDay_RosterWeek_Id(weekId);
    }

    private int planningPriority(int dayOfWeek) {

        // Monday=1 ... Sunday=7

        return switch (dayOfWeek) {
            case 6 -> 1; // Saturday
            case 7 -> 2; // Sunday
            case 1 -> 3; // Monday
            case 2 -> 4; // Tuesday
            case 3 -> 5; // Wednesday
            case 4 -> 6; // Thursday
            case 5 -> 7; // Friday
            default -> 99;
        };
    }
}

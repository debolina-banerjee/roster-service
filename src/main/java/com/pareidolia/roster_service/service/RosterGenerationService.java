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

        List<RosterDay> days =
                rosterDayRepository.findByRosterWeekId(rosterWeek.getId());

        // 2. Plan normal shifts
        for (RosterDay day : days) {
            shiftPlannerService.planDay(day);
        }

        // 3. ON_DUTY topup
        onDutyTopupService.fillOnDuty(weekStartDate);


//        weekendNightDonorRebalanceService.execute(weekStartDate);

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
}

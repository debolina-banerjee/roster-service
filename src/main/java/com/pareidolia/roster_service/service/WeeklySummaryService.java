package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.ShiftAssignment;
import com.pareidolia.roster_service.entity.WeeklySummary;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.LeaveImportRepository;
import com.pareidolia.roster_service.repository.ShiftAssignmentRepository;
import com.pareidolia.roster_service.repository.WeeklySummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.pareidolia.roster_service.enumtype.ShiftCode.GRAVEYARD;
import static com.pareidolia.roster_service.enumtype.ShiftCode.NIGHT;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class WeeklySummaryService {
    private final ShiftAssignmentRepository assignmentRepo;
    private final WeeklySummaryRepository summaryRepo;
    private final LeaveImportRepository leaveRepo;

    public void calculateSummary(Long weekId, LocalDate weekStart) {

        List<ShiftAssignment> all =
                assignmentRepo.findByRosterDay_RosterWeek_Id(weekId);

        Map<Long, List<ShiftAssignment>> byEmp =
                all.stream().collect(groupingBy(a -> a.getEmployee().getId()));

        for (var entry : byEmp.entrySet()) {

            Long empId = entry.getKey();
            List<ShiftAssignment> list = entry.getValue();

            int regular = list.stream()
                    .filter(a -> !a.isOnDuty())
                    .mapToInt(ShiftAssignment::getActualHours)
                    .sum();

            int onDuty = list.stream()
                    .filter(ShiftAssignment::isOnDuty)
                    .mapToInt(ShiftAssignment::getActualHours)
                    .sum();

            int nights = (int) list.stream()
                    .filter(a -> a.getShiftType().getCode() == NIGHT)
                    .count();

            int graves = (int) list.stream()
                    .filter(a -> a.getShiftType().getCode() == GRAVEYARD)
                    .count();

            int evenings = (int) list.stream()
                    .filter(a -> a.getShiftType().getCode() == ShiftCode.EVENING)
                    .count();

            int drags = (int) list.stream()
                    .filter(ShiftAssignment::isDragged)
                    .count();

            int leaves =
                    leaveRepo.countByEmployeeAndWeek(
                            empId,
                            weekStart,
                            weekStart.plusDays(6));

            int total = regular + onDuty;

            // 🔥 FAIRNESS SCORE
            int nightFamily = nights + graves;
            String fairnessFlag =
                    nightFamily >= 4 ? "OVERLOADED"
                            : nightFamily >= 2 ? "BALANCED"
                            : "OK";

            // ✅ UPSERT (VERY IMPORTANT)
            WeeklySummary s =
                    summaryRepo
                            .findByEmployeeIdAndWeekStart(empId, weekStart)
                            .orElse(new WeeklySummary());

            s.setEmployeeId(empId);
            s.setWeekStart(weekStart);
            s.setRegularHours(regular);
            s.setOnDutyHours(onDuty);
            s.setTotalHours(total);
            s.setDeficit(48 - total);
            s.setNights(nights);
            s.setGraveyards(graves);
            s.setEvenings(evenings);
            s.setDrags(drags);
            s.setLeaves(leaves);
            s.setFairnessFlag(fairnessFlag);

            summaryRepo.save(s);
        }
    }}



package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ShiftAssignmentRepository assignmentRepo;
    private final WeeklySummaryRepository summaryRepo;
    private final LeaveImportRepository leaveRepo;
    private final WeeklyOffRepository weeklyOffRepo;

    // ======================================================
    // 1. WEEKLY ROSTER CSV
    // ======================================================
    public byte[] generateRosterCsv(LocalDate weekStart) {

        List<ShiftAssignment> list =
                assignmentRepo.findByRosterDay_DayDateBetween(
                        weekStart,
                        weekStart.plusDays(6)
                );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter w = new PrintWriter(out);

        w.println("Date,EmployeeCode,EmployeeName,Gender,Shift,OnDuty,Dragged,ActualHours,Leave,WeeklyOff");

        for (ShiftAssignment sa : list) {

            Employee e = sa.getEmployee();
            LocalDate date = sa.getRosterDay().getDayDate();

            boolean leave =
                    leaveRepo.findByEmployee_IdAndLeaveDate(e.getId(), date)
                            .isPresent();

            boolean off =
                    weeklyOffRepo.existsByEmployee_IdAndOffDate(e.getId(), date);

            w.printf(
                    "%s,%s,%s,%s,%s,%s,%s,%d,%s,%s%n",
                    date,
                    e.getEmployeeCode(),
                    e.getFullName(),
                    e.getGender(),
                    sa.getShiftType().getCode(),
                    sa.isOnDuty() ? "Y" : "N",
                    sa.isDragged() ? "Y" : "N",
                    sa.getActualHours(),
                    leave ? "Y" : "N",
                    off ? "Y" : "N"
            );
        }

        w.flush();
        return out.toByteArray();
    }

    // ======================================================
    // 2. WEEKLY SUMMARY CSV
    // ======================================================
    public byte[] generateSummaryCsv(LocalDate weekStart) {

        List<WeeklySummary> list =
                summaryRepo.findByWeekStart(weekStart);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter w = new PrintWriter(out);

        w.println("EmployeeId,RegularHours,OnDutyHours,Total,Deficit,Nights,Graveyards,Drags,Leaves");

        for (WeeklySummary s : list) {

            w.printf(
                    "%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    s.getEmployeeId(),
                    s.getRegularHours(),
                    s.getOnDutyHours(),
                    s.getTotalHours(),
                    s.getDeficit(),
                    s.getNights(),
                    s.getGraveyards(),
                    s.getDrags(),
                    s.getLeaves()
            );
        }

        w.flush();
        return out.toByteArray();
    }

    // ======================================================
    // 3. AUDIT CSV – RULE CHECKING VIEW
    // ======================================================
    public byte[] generateAuditCsv(LocalDate weekStart) {

        List<ShiftAssignment> list =
                assignmentRepo.findByRosterDay_DayDateBetween(
                        weekStart,
                        weekStart.plusDays(6)
                );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter w = new PrintWriter(out);

        w.println("Date,Employee,Shift,FemaleNight,PostLeave,WeeklyOff,Dragged,OnDuty");

        for (ShiftAssignment sa : list) {

            Employee e = sa.getEmployee();
            LocalDate date = sa.getRosterDay().getDayDate();

            boolean femaleNight =
                    e.getGender().name().equals("FEMALE") &&
                            (sa.getShiftType().getCode().name().equals("NIGHT")
                                    || sa.getShiftType().getCode().name().equals("GRAVEYARD"));

            boolean postLeave =
                    leaveRepo.findByEmployee_IdAndLeaveDate(
                            e.getId(),
                            date.minusDays(1)
                    ).isPresent();

            boolean off =
                    weeklyOffRepo.existsByEmployee_IdAndOffDate(
                            e.getId(),
                            date
                    );

            w.printf(
                    "%s,%s,%s,%s,%s,%s,%s,%s%n",
                    date,
                    e.getEmployeeCode(),
                    sa.getShiftType().getCode(),
                    femaleNight ? "VIOLATION" : "OK",
                    postLeave ? "POST_LEAVE" : "OK",
                    off ? "WEEK_OFF" : "OK",
                    sa.isDragged() ? "DRAGGED" : "NORMAL",
                    sa.isOnDuty() ? "ON_DUTY" : "REGULAR"
            );
        }

        w.flush();
        return out.toByteArray();
    }
}

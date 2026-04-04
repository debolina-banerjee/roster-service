package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.*;
import com.pareidolia.roster_service.util.ExcelStyleUtil;
import com.pareidolia.roster_service.util.DateUtil;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final ShiftAssignmentRepository assignmentRepo;
    private final EmployeeRepository employeeRepo;
    private final WeeklyOffRepository weeklyOffRepo;
    private final LeaveImportRepository leaveRepo;
    private final ShiftConfigRepository shiftConfigRepo;

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("EEE, dd-MMM");

    // =====================================================
    // MAIN MATRIX
    // =====================================================
    public byte[] weeklyMatrix(LocalDate weekStart) throws Exception {

        Long weekId = getWeekId(weekStart); // ✅ fetch once

        List<ShiftAssignment> list =
                assignmentRepo.findByRosterDay_DayDateBetween(
                        weekStart, weekStart.plusDays(6));

        Map<Long, Map<LocalDate, ShiftAssignment>> map =
                list.stream().collect(
                        Collectors.groupingBy(
                                a -> a.getEmployee().getId(),
                                Collectors.toMap(
                                        a -> a.getRosterDay().getDayDate(),
                                        a -> a,
                                        (a, b) -> a // safety merge
                                )
                        )
                );

        List<Employee> employees =
                employeeRepo.findByActiveTrue()
                        .stream()
                        .sorted(Comparator.comparing(Employee::getEmployeeCode))
                        .toList();

        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("Roster");

        // styles
        CellStyle h = ExcelStyleUtil.header(wb);
        CellStyle early = ExcelStyleUtil.color(wb, IndexedColors.PALE_BLUE);
        CellStyle evening = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_ORANGE);
        CellStyle night = ExcelStyleUtil.color(wb, IndexedColors.LAVENDER);
        CellStyle grave = ExcelStyleUtil.color(wb, IndexedColors.GREY_25_PERCENT);
        CellStyle onDuty = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_GREEN);
        CellStyle off = ExcelStyleUtil.color(wb, IndexedColors.YELLOW);
        CellStyle leave = ExcelStyleUtil.color(wb, IndexedColors.ROSE);

        // HEADER
        Row r0 = s.createRow(0);
        r0.createCell(0).setCellValue("Employee");
        r0.getCell(0).setCellStyle(h);

        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            Cell c = r0.createCell(i + 1);
            c.setCellValue(d.format(DAY_FMT));
            c.setCellStyle(h);
        }

        int row = 1;

        for (Employee e : employees) {

            Row r = s.createRow(row++);
            r.createCell(0)
                    .setCellValue(e.getEmployeeCode() + " - " + e.getFullName());

            for (int i = 0; i < 7; i++) {

                LocalDate d = weekStart.plusDays(i);
                Cell c = r.createCell(i + 1);

                boolean isOff =
                        weeklyOffRepo.existsByEmployee_IdAndOffDate(e.getId(), d);

                boolean isLeave =
                        leaveRepo.findByEmployee_IdAndLeaveDate(e.getId(), d).isPresent();

                if (isLeave) {
                    c.setCellValue("LEAVE");
                    c.setCellStyle(leave);
                    continue;
                }

                if (isOff) {
                    c.setCellValue("W/O");
                    c.setCellStyle(off);
                    continue;
                }

                ShiftAssignment sa =
                        map.getOrDefault(e.getId(), Map.of()).get(d);

                if (sa == null) {
                    c.setCellValue("-");
                    continue;
                }

                ShiftCode sc = sa.getShiftType().getCode();
                c.setCellValue(sc.name());

                switch (sc) {
                    case EARLY_MORNING -> c.setCellStyle(early);
                    case EVENING -> c.setCellStyle(evening);
                    case NIGHT -> c.setCellStyle(night);
                    case GRAVEYARD -> c.setCellStyle(grave);
                    case ON_DUTY -> c.setCellStyle(onDuty);
                }
            }
        }

        for (int i = 0; i < 8; i++) s.autoSizeColumn(i);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // ✅ summary
        buildShiftSummarySheet(wb, weekStart, weekId);
        buildEmployeeFairnessSheet(wb, weekStart, weekId);

        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    // =====================================================
    // SHIFT SUMMARY SHEET
    // =====================================================
    private void buildShiftSummarySheet(
            Workbook wb,
            LocalDate weekStart,
            Long weekId) {

        Sheet sheet = wb.createSheet("Shift Summary");

        CellStyle header = ExcelStyleUtil.header(wb);
        CellStyle ok = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_GREEN);
        CellStyle under = ExcelStyleUtil.color(wb, IndexedColors.ROSE);
        CellStyle over = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_ORANGE);

        Row h = sheet.createRow(0);
        String[] cols = {"Date", "Shift", "Required", "Actual", "Status"};

        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        int rowIdx = 1;

        for (int i = 0; i < 7; i++) {

            LocalDate date = weekStart.plusDays(i);

            DayCategory dayCategory =
                    DateUtil.isWeekend(date)
                            ? DayCategory.WEEKEND
                            : DayCategory.WEEKDAY;

            List<ShiftConfig> configs =
                    shiftConfigRepo
                            .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                    weekId,
                                    dayCategory);

            for (ShiftConfig cfg : configs) {

                ShiftCode code = cfg.getShiftType().getCode();
                if (code == ShiftCode.ON_DUTY) continue;

                int required = cfg.getRequiredResources();

                long actual =
                        assignmentRepo
                                .countByRosterDay_DayDateAndShiftType_Code(
                                        date,
                                        code);

                String status;
                CellStyle style;

                if (actual < required) {
                    status = "UNDER";
                    style = under;
                } else if (actual > required) {
                    status = "OVER";
                    style = over;
                } else {
                    status = "OK";
                    style = ok;
                }

                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(date.format(DAY_FMT));
                r.createCell(1).setCellValue(code.name());
                r.createCell(2).setCellValue(required);
                r.createCell(3).setCellValue(actual);

                Cell statusCell = r.createCell(4);
                statusCell.setCellValue(status);
                statusCell.setCellStyle(style);
            }
        }

        for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);
    }

    // =====================================================
    // WEEK RESOLVER
    // =====================================================
    private Long getWeekId(LocalDate weekStart) {

        Long weekId =
                assignmentRepo.findWeekIdByStartDate(weekStart);

        if (weekId == null) {
            throw new IllegalStateException(
                    "No roster week found for start date: " + weekStart);
        }

        return weekId;
    }
    private void buildEmployeeFairnessSheet(
            Workbook wb,
            LocalDate weekStart,
            Long weekId) {

        Sheet sheet = wb.createSheet("Employee Fairness");

        CellStyle header = ExcelStyleUtil.header(wb);
        CellStyle ok = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_GREEN);
        CellStyle warn = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_ORANGE);
        CellStyle bad = ExcelStyleUtil.color(wb, IndexedColors.ROSE);

        String[] cols = {
                "Employee",
                "Total Hours",
                "Night Count",
                "Graveyard Count",
                "Evening Count",
                "Drags",
                "Leaves",
                "Fairness"
        };

        Row h = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        List<ShiftAssignment> weekData =
                assignmentRepo.findByRosterDay_RosterWeek_Id(weekId);

        Map<Long, List<ShiftAssignment>> byEmp =
                weekData.stream()
                        .collect(Collectors.groupingBy(
                                a -> a.getEmployee().getId()));

        List<Employee> employees =
                employeeRepo.findByActiveTrue()
                        .stream()
                        .sorted(Comparator.comparing(Employee::getEmployeeCode))
                        .toList();

        int rowIdx = 1;

        for (Employee e : employees) {

            List<ShiftAssignment> list =
                    byEmp.getOrDefault(e.getId(), List.of());

            int totalHours = list.stream()
                    .mapToInt(ShiftAssignment::getActualHours)
                    .sum();

            long nights = list.stream()
                    .filter(a -> a.getShiftType().getCode() == ShiftCode.NIGHT)
                    .count();

            long graves = list.stream()
                    .filter(a -> a.getShiftType().getCode() == ShiftCode.GRAVEYARD)
                    .count();

            long evenings = list.stream()
                    .filter(a -> a.getShiftType().getCode() == ShiftCode.EVENING)
                    .count();

            long drags = list.stream()
                    .filter(ShiftAssignment::isDragged)
                    .count();

            int leaves =
                    leaveRepo.countByEmployeeAndWeek(
                            e.getId(),
                            weekStart,
                            weekStart.plusDays(6));

            // 🔥 FAIRNESS LOGIC (tuneable)
            String fairness;
            CellStyle style;

            long nightFamily = nights + graves;

            if (nightFamily >= 4) {
                fairness = "OVERLOADED";
                style = bad;
            } else if (nightFamily >= 2) {
                fairness = "BALANCED";
                style = warn;
            } else {
                fairness = "OK";
                style = ok;
            }

            Row r = sheet.createRow(rowIdx++);

            r.createCell(0).setCellValue(
                    e.getEmployeeCode() + " - " + e.getFullName());
            r.createCell(1).setCellValue(totalHours);
            r.createCell(2).setCellValue(nights);
            r.createCell(3).setCellValue(graves);
            r.createCell(4).setCellValue(evenings);
            r.createCell(5).setCellValue(drags);
            r.createCell(6).setCellValue(leaves);

            Cell f = r.createCell(7);
            f.setCellValue(fairness);
            f.setCellStyle(style);
        }

        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
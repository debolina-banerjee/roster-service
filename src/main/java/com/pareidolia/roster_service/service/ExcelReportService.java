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
    private final WeeklyOffPreferenceRepository preferenceRepo;

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
        buildPreferenceAuditSheet(wb, weekStart);

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
    private void buildPreferenceAuditSheet(
            Workbook wb,
            LocalDate weekStart) {

        Sheet sheet = wb.createSheet("Preference Audit");

        CellStyle header = ExcelStyleUtil.header(wb);
        CellStyle okStyle = ExcelStyleUtil.color(wb, IndexedColors.LIGHT_GREEN);
        CellStyle noStyle = ExcelStyleUtil.color(wb, IndexedColors.ROSE);

        Row h = sheet.createRow(0);

        String[] cols = {
                "Emp Code",
                "Name",
                "Preferred Date",
                "Actual W/O",
                "Honored"
        };

        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        List<WeeklyOffPreference> prefs =
                preferenceRepo.findByWeekStartDate(weekStart);

//        List<WeeklyOffPreference> rawPrefs =
//                preferenceRepo.findByWeekStartDate(weekStart);
//
//        Map<String, WeeklyOffPreference> prefMap = new LinkedHashMap<>();
//
//        for (WeeklyOffPreference p : rawPrefs) {
//            prefMap.put(p.getEmployeeCode(), p);
//        }
//
//        List<WeeklyOffPreference> prefs =
//                new ArrayList<>(prefMap.values());

        int rowIdx = 1;
        int honored = 0;

        for (WeeklyOffPreference pref : prefs) {

            Employee emp =
                    employeeRepo.findByEmployeeCode(pref.getEmployeeCode())
                            .orElse(null);

            if (emp == null) continue;

            Optional<WeeklyOff> offOpt =
                    weeklyOffRepo.findFirstByEmployee_IdAndWeekStartDate(
                            emp.getId(),
                            weekStart
                    );

            LocalDate actualOff =
                    offOpt.map(WeeklyOff::getOffDate).orElse(null);

            boolean isHonored =
                    actualOff != null &&
                            actualOff.equals(pref.getPreferredDate());

            if (isHonored) honored++;

            Row r = sheet.createRow(rowIdx++);

            r.createCell(0).setCellValue(emp.getEmployeeCode());
            r.createCell(1).setCellValue(emp.getFullName());
            r.createCell(2).setCellValue(pref.getPreferredDate().toString());
            r.createCell(3).setCellValue(
                    actualOff != null ? actualOff.toString() : "-"
            );

            Cell statusCell = r.createCell(4);
            statusCell.setCellValue(isHonored ? "Yes" : "No");
            statusCell.setCellStyle(isHonored ? okStyle : noStyle);
        }

        rowIdx++;

        Row r1 = sheet.createRow(rowIdx++);
        r1.createCell(0).setCellValue("Total Submitted");
        r1.createCell(1).setCellValue(prefs.size());

        Row r2 = sheet.createRow(rowIdx++);
        r2.createCell(0).setCellValue("Honored");
        r2.createCell(1).setCellValue(honored);

        Row r3 = sheet.createRow(rowIdx++);
        r3.createCell(0).setCellValue("Missed");
        r3.createCell(1).setCellValue(prefs.size() - honored);

        Row r4 = sheet.createRow(rowIdx++);
        r4.createCell(0).setCellValue("Success %");
        r4.createCell(1).setCellValue(
                prefs.isEmpty() ? 0 :
                        (honored * 100.0 / prefs.size())
        );

        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
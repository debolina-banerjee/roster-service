package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.*;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final RosterWeekRepository rosterWeekRepo;
    private final EmployeeRepository employeeRepo;
    private final RosterDayRepository rosterDayRepo;
    private final ShiftAssignmentRepository shiftAssignmentRepo;
    private final ShiftConfigRepository shiftConfigRepo;

    public byte[] weeklyMatrix(LocalDate weekStart) throws Exception {

        RosterWeek week = rosterWeekRepo.findByWeekStartDate(weekStart)
                .orElseThrow(() -> new RuntimeException("Week not found"));

        Long weekId = week.getId();

        List<Employee> employees = employeeRepo.findAll();
        List<RosterDay> days = rosterDayRepo.findByRosterWeekId(weekId);
        List<ShiftAssignment> assignments =
                shiftAssignmentRepo.findByRosterDay_RosterWeek_Id(weekId);

        Map<Long, Map<LocalDate, ShiftAssignment>> map = new HashMap<>();

        for (ShiftAssignment sa : assignments) {
            if (sa.getEmployee() == null || sa.getRosterDay() == null) continue;

            map.computeIfAbsent(sa.getEmployee().getId(), k -> new HashMap<>())
                    .put(sa.getRosterDay().getDayDate(), sa);
        }

        // ✅ FIXED: try-with-resources
        try (Workbook wb = new XSSFWorkbook()) {

            Sheet sheet = wb.createSheet("Roster");

            int rowIdx = 0;

            // HEADER
            Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("Employee");

            int colIdx = 1;
            for (RosterDay d : days) {
                header.createCell(colIdx++).setCellValue(d.getDayDate().toString());
            }

            // DATA
            for (Employee e : employees) {
                Row r = sheet.createRow(rowIdx++);

                // ✅ FIXED
                r.createCell(0).setCellValue(
                        e.getFullName() != null ? e.getFullName() : "EMP-" + e.getId()
                );

                colIdx = 1;

                for (RosterDay d : days) {
                    Cell c = r.createCell(colIdx++);

                    ShiftAssignment sa =
                            map.getOrDefault(e.getId(), Map.of())
                                    .get(d.getDayDate());

                    // ✅ SAFE NULL HANDLING
                    if (sa == null || sa.getShiftType() == null) {
                        c.setCellValue("-");
                        continue;
                    }

                    ShiftCode sc = sa.getShiftType().getCode();
                    c.setCellValue(sc.name());
                }
            }

            // SUMMARY
            Sheet summary = wb.createSheet("Summary");

            int sRowIdx = 0;

            for (RosterDay d : days) {

                Row r = summary.createRow(sRowIdx++);
                r.createCell(0).setCellValue(d.getDayDate().toString());

                DayCategory dayCategory = d.getDayCategory();

                List<ShiftConfig> configs =
                        shiftConfigRepo.findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                weekId,
                                dayCategory);

                if (configs == null || configs.isEmpty()) continue;

                Map<ShiftCode, Long> actual =
                        assignments.stream()
                                .filter(sa -> sa.getRosterDay() != null &&
                                        sa.getRosterDay().getDayDate().equals(d.getDayDate()))
                                .filter(sa -> sa.getShiftType() != null)
                                .collect(Collectors.groupingBy(
                                        sa -> sa.getShiftType().getCode(),
                                        Collectors.counting()
                                ));

                int cIdx = 1;

                for (ShiftConfig cfg : configs) {

                    if (cfg.getShiftType() == null) continue;

                    ShiftCode sc = cfg.getShiftType().getCode();

                    // ✅ FIXED
                    long required = cfg.getRequiredResources();
                    long actualCount = actual.getOrDefault(sc, 0L);

                    r.createCell(cIdx++)
                            .setCellValue(sc + " (" + actualCount + "/" + required + ")");
                }
            }

            for (int i = 0; i < days.size() + 1; i++) {
                sheet.autoSizeColumn(i);
            }

            try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                wb.write(bos);
                return bos.toByteArray();
            }
        }
    }
}
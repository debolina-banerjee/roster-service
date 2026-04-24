package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.WeeklyOffPreference;
import com.pareidolia.roster_service.repository.EmployeeRepository;
import com.pareidolia.roster_service.repository.WeeklyOffPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyOffPreferenceUploadService {
    private final WeeklyOffPreferenceRepository preferenceRepository;
    private final EmployeeRepository employeeRepository;

    public void process(MultipartFile file, LocalDate weekStartDate) {

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);

            // 🚨 Track per-day count (cap enforcement)
            Map<LocalDate, Integer> countPerDay = new HashMap<>();

            for (Row row : sheet) {

                try {

                    if (row.getRowNum() == 0) continue;

                    Cell empCell = row.getCell(0);

                    if (empCell == null || empCell.getCellType() != CellType.STRING) {
                        log.warn("❌ Invalid employee cell at row {}", row.getRowNum());
                        continue;
                    }

                    String empCode = empCell.getStringCellValue().trim();

                    // ===============================
                    // ✅ SAFE DATE PARSING
                    // ===============================
                    Cell dateCell = row.getCell(1);

                    if (dateCell == null) {
                        log.warn("❌ Empty date cell at row {}", row.getRowNum());
                        continue;
                    }

                    LocalDate preferredDate;

                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        preferredDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    } else if (dateCell.getCellType() == CellType.STRING) {
                        preferredDate = LocalDate.parse(dateCell.getStringCellValue().trim());
                    } else {
                        log.warn("❌ Unsupported date format at row {}", row.getRowNum());
                        continue;
                    }

                    if (!employeeRepository.existsByEmployeeCode(empCode)) {
                        log.warn("❌ Invalid employee skipped: {}", empCode);
                        continue;
                    }

                    if (!preferredDate.isAfter(weekStartDate.minusDays(1)) ||
                            !preferredDate.isBefore(weekStartDate.plusDays(7))) {

                        log.warn("❌ Invalid date outside week: {}", preferredDate);
                        continue;
                    }

                    int inMemoryCount = countPerDay.getOrDefault(preferredDate, 0);
                    int dbCount = preferenceRepository.countByPreferredDate(preferredDate);

                    int total = inMemoryCount + dbCount;

                    boolean isWeekday =
                            preferredDate.getDayOfWeek().getValue() <= 5;

                    int cap = isWeekday ? 2 : 4;

                    if (total >= cap) {
                        log.warn("⚠️ Cap reached for {} → skipping {}", preferredDate, empCode);
                        continue;
                    }

                    WeeklyOffPreference pref = WeeklyOffPreference.builder()
                            .employeeCode(empCode)
                            .preferredDate(preferredDate)
                            .weekStartDate(weekStartDate)
                            .build();

                    preferenceRepository.save(pref);

                    countPerDay.put(preferredDate, inMemoryCount + 1);

                } catch (Exception ex) {
                    log.error("❌ Row failed at rowNum={} → skipping",
                            row.getRowNum(), ex);
                    continue; // 🔥 THIS IS THE KEY LINE
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel processing failed", e);
        }
    }
}

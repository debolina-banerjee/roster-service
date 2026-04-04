package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.WeeklyOff;
import com.pareidolia.roster_service.entity.WeeklyOffPreference;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.repository.EmployeeRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import com.pareidolia.roster_service.repository.WeeklyOffPreferenceRepository;
import com.pareidolia.roster_service.repository.WeeklyOffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyOffService {

    private final WeeklyOffRepository weeklyOffRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftConfigRepository shiftConfigRepository;
    private final WeeklyOffPreferenceRepository preferenceRepository;

    @Transactional
    public void generateWeeklyOffs(Long rosterWeekId, LocalDate weekStart) {

        // =====================================================
        // 1️⃣ FETCH EMPLOYEES
        // =====================================================
        List<Employee> employees =
                employeeRepository.findByActiveTrue()
                        .stream()
                        .sorted(Comparator.comparingLong(Employee::getId))
                        .toList();

        int totalEmployees = employees.size();

        // =====================================================
        // 2️⃣ DELETE OLD OFFS
        // =====================================================
        employees.forEach(e ->
                weeklyOffRepository
                        .findByEmployee_IdAndWeekStartDate(e.getId(), weekStart)
                        .forEach(weeklyOffRepository::delete)
        );

        // =====================================================
        // 3️⃣ SHIFT DEMAND
        // =====================================================
        List<ShiftConfig> configs =
                shiftConfigRepository.findActiveByWeek(rosterWeekId);

        int weekdayDemand = configs.stream()
                .filter(sc -> sc.getDayCategory() == DayCategory.WEEKDAY)
                .mapToInt(ShiftConfig::getRequiredResources)
                .sum();

        int minRequiredAvailable = weekdayDemand + 1;

        log.info("Weekday demand = {}", weekdayDemand);
        log.info("Minimum availability = {}", minRequiredAvailable);

        // =====================================================
        // 4️⃣ LIMITS
        // =====================================================
        int weekdayCap = 2;
        int weekendCap = 5;

        // =====================================================
        // 5️⃣ DAY ORDER
        // =====================================================
        List<Integer> dayOrder = List.of(
                5, // Saturday
                6, // Sunday
                0, // Monday
                1, // Tuesday
                2, // Wednesday
                3, // Thursday
                4  // Friday
        );

        int[] offsPerDay = new int[7];

        // =====================================================
        // 6️⃣ APPLY PREFERENCES FIRST
        // =====================================================
        Set<Long> preferenceAssigned =
                assignPreferredWeeklyOffs(
                        weekStart,
                        offsPerDay,
                        weekdayCap,
                        weekendCap,
                        totalEmployees,
                        minRequiredAvailable
                );

        long pointer = rosterWeekId % employees.size();

        // =====================================================
        // 7️⃣ ASSIGN REMAINING OFFS
        // =====================================================
        for (Employee e : employees) {

            // Skip employees who already got preferred weekly off
            if (preferenceAssigned.contains(e.getId())) {
                continue;
            }

            int attempts = 0;
            boolean assigned = false;

            while (attempts < 14) {

                int dayIndex = dayOrder.get(Math.floorMod(pointer, 7));
                boolean isWeekday = dayIndex <= 4;

                if (isWeekday) {

                    int projectedAvailableToday =
                            totalEmployees - (offsPerDay[dayIndex] + 1);

                    if (projectedAvailableToday < minRequiredAvailable) {
                        pointer++;
                        attempts++;
                        continue;
                    }
                }

                int weekdayOffs =
                        offsPerDay[0] + offsPerDay[1] + offsPerDay[2]
                                + offsPerDay[3] + offsPerDay[4];

                int projectedWeekdayAvailable =
                        totalEmployees - weekdayOffs;

                if (isWeekday && projectedWeekdayAvailable <= minRequiredAvailable) {
                    pointer++;
                    attempts++;
                    continue;
                }

                boolean allowed =
                        (isWeekday && offsPerDay[dayIndex] < weekdayCap) ||
                                (!isWeekday && offsPerDay[dayIndex] < weekendCap);

                if (!allowed) {
                    pointer++;
                    attempts++;
                    continue;
                }

                LocalDate offDate = weekStart.plusDays(dayIndex);

                WeeklyOff off = WeeklyOff.builder()
                        .employee(e)
                        .weekStartDate(weekStart)
                        .offDate(offDate)
                        .build();

                weeklyOffRepository.save(off);

                offsPerDay[dayIndex]++;
                pointer++;
                assigned = true;
                break;
            }

            // fallback if no slot found
            // fallback if no slot found
            if (!assigned) {

                for (int i = 0; i < 7; i++) {

                    int dayIndex = dayOrder.get((int)((pointer + i) % 7));
                    boolean isWeekday = dayIndex <= 4;

                    int cap = isWeekday ? weekdayCap : weekendCap;

                    if (offsPerDay[dayIndex] >= cap) {
                        continue; // 🚨 do NOT exceed cap
                    }

                    LocalDate offDate = weekStart.plusDays(dayIndex);

                    WeeklyOff off = WeeklyOff.builder()
                            .employee(e)
                            .weekStartDate(weekStart)
                            .offDate(offDate)
                            .build();

                    weeklyOffRepository.save(off);

                    offsPerDay[dayIndex]++;
                    pointer++;

                    assigned = true;
                    break;
                }

                // 🚨 HARD FAIL-SAFE (should never happen)
                if (!assigned) {
                    log.error("❌ No slot available for employee={} due to strict caps",
                            e.getEmployeeCode());
                }
            }
        }

        log.info(
                "WeeklyOff distribution → Mon={} Tue={} Wed={} Thu={} Fri={} Sat={} Sun={}",
                offsPerDay[0],
                offsPerDay[1],
                offsPerDay[2],
                offsPerDay[3],
                offsPerDay[4],
                offsPerDay[5],
                offsPerDay[6]
        );

        analyzePreferences(weekStart);
    }

    // =====================================================
    // ASSIGN PREFERRED WEEKLY OFF
    // =====================================================
    private Set<Long> assignPreferredWeeklyOffs(
            LocalDate weekStart,
            int[] offsPerDay,
            int weekdayCap,
            int weekendCap,
            int totalEmployees,
            int minRequiredAvailable) {

        Set<Long> assignedEmployees = new HashSet<>();

        List<WeeklyOffPreference> preferences =
                preferenceRepository.findByWeekStartDate(weekStart);

        for (WeeklyOffPreference pref : preferences) {

            Employee emp =
                    employeeRepository
                            .findByEmployeeCode(pref.getEmployeeCode())
                            .orElse(null);

            if (emp == null) continue;

            if (assignedEmployees.contains(emp.getId())) continue;

            int dayIndex =
                    pref.getPreferredDate().getDayOfWeek().getValue() - 1;

            boolean isWeekday = dayIndex <= 4;

            int projectedAvailable =
                    totalEmployees - (offsPerDay[dayIndex] + 1);

            if (isWeekday && projectedAvailable < minRequiredAvailable) continue;

            if (isWeekday && offsPerDay[dayIndex] >= weekdayCap) continue;

            if (!isWeekday && offsPerDay[dayIndex] >= weekendCap) continue;

            WeeklyOff off = WeeklyOff.builder()
                    .employee(emp)
                    .weekStartDate(weekStart)
                    .offDate(pref.getPreferredDate())
                    .build();

            weeklyOffRepository.save(off);

            offsPerDay[dayIndex]++;
            assignedEmployees.add(emp.getId());
        }

        return assignedEmployees;
    }
    // =====================================================
// 📊 PREFERENCE ANALYSIS
// =====================================================
    public void analyzePreferences(LocalDate weekStart) {
        System.out.println("analyzePreferences CALLED for week = " + weekStart);

        List<WeeklyOffPreference> preferences =
                preferenceRepository.findByWeekStartDate(weekStart);

        int total = preferences.size();
        int satisfied = 0;

        for (WeeklyOffPreference pref : preferences) {

            Employee emp =
                    employeeRepository
                            .findByEmployeeCode(pref.getEmployeeCode())
                            .orElse(null);

            if (emp == null) continue;

            boolean isSatisfied =
                    weeklyOffRepository.existsByEmployee_IdAndOffDate(
                            emp.getId(),
                            pref.getPreferredDate()
                    );

            if (isSatisfied) {
                satisfied++;
            } else {

                String reason = detectFailureReason(pref, emp);

                log.warn("❌ Preference FAILED → emp={} date={} reason={}",
                        pref.getEmployeeCode(),
                        pref.getPreferredDate(),
                        reason);
            }
        }

        double percentage =
                total == 0 ? 100 : (satisfied * 100.0) / total;

        log.info("📊 Preference Satisfaction = {} / {} → {}%",
                satisfied, total, percentage);
    }

    // =====================================================
// ❌ FAILURE REASON DETECTOR
// =====================================================
    private String detectFailureReason(
            WeeklyOffPreference pref,
            Employee emp) {

        LocalDate date = pref.getPreferredDate();

        int dayIndex =
                date.getDayOfWeek().getValue() - 1;

        boolean isWeekday = dayIndex <= 4;

        int cap = isWeekday ? 2 : 5;

        long count =
                weeklyOffRepository.findAll().stream()
                        .filter(off -> off.getOffDate().equals(date))
                        .count();

        if (count >= cap) {
            return "CAP_REACHED";
        }

        return "SHIFT_CONSTRAINT";
    }

}
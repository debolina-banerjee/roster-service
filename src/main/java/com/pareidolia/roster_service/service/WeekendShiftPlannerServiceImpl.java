

package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.Gender;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.*;
import com.pareidolia.roster_service.service.context.RosterContext;
import com.pareidolia.roster_service.service.context.RosterContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.pareidolia.roster_service.enumtype.ShiftCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeekendShiftPlannerServiceImpl implements WeekendShiftPlannerService {

    private final ShiftConfigRepository shiftConfigRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final LeaveImportRepository leaveImportRepository;

    private final ShiftTypeRepository  shiftTypeRepository;

    private final RosterContextBuilder contextBuilder;
    private final ValidationService validationService;
    private final ShiftAssignmentService assignmentService;
    private final WeeklyOffRepository weeklyOffRepository;

    private static final List<ShiftCode> PRIORITY =
            List.of(EARLY_MORNING, NIGHT,GRAVEYARD,EVENING, ON_DUTY);

    @Override
    @Transactional
    public void planDay(RosterDay rosterDay) {

        log.info(">>> planDay started | date={} | weekId={} | category={}",
                rosterDay.getDayDate(),
                rosterDay.getRosterWeek().getId(),
                rosterDay.getDayCategory());

        if (shiftAssignmentRepository.existsByRosterDay_Id(rosterDay.getId()))
            return;

        Long weekId = rosterDay.getRosterWeek().getId();


        //New Addition - 1

        boolean isWeekend =
                rosterDay.getDayCategory() ==
                        com.pareidolia.roster_service.enumtype.DayCategory.WEEKEND;

        List<ShiftConfig> shiftConfigs =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                weekId,
                                rosterDay.getDayCategory()
                        );

        shiftConfigs.sort(Comparator.comparingInt(
                c -> PRIORITY.indexOf(c.getShiftType().getCode())
        ));

        List<Employee> employees =
                employeeRepository.findActiveNotOnLeave(
                        rosterDay.getDayDate()
                );

        Collections.shuffle(employees, new Random(weekId));

        employees.sort(
                Comparator
                        .comparingLong(
                                (Employee e) -> shiftAssignmentRepository
                                        .sumWeeklyHours(e.getId(), weekId)
                        )
                        .thenComparing(e -> 0)
        );

        Set<Long> assignedToday = new HashSet<>();

        Map<ShiftCode, Integer> assignedPerShift = new HashMap<>();
        for (ShiftConfig cfg : shiftConfigs) {
            assignedPerShift.put(cfg.getShiftType().getCode(), 0);
        }

        int eveningRequired = getRequired(shiftConfigs, EVENING);
        int graveyardRequired = getRequired(shiftConfigs, GRAVEYARD);

        // ================= PASS 1 =================
        for (ShiftConfig config : shiftConfigs) {

            ShiftCode sc = config.getShiftType().getCode();
            int required = config.getRequiredResources();

            if (required == 0 || sc == ON_DUTY) continue;

            for (Employee emp : employees) {

                if (assignedToday.contains(emp.getId())) continue;

                int current = assignedPerShift.get(sc);
                if (current >= required) break;

                int remainingEmployees =
                        employees.size() - assignedToday.size();

                int eveningRemaining =
                        eveningRequired - assignedPerShift.getOrDefault(EVENING, 0);

//                int graveyardRemaining =
//                        graveyardRequired - assignedPerShift.getOrDefault(GRAVEYARD, 0);

                long liveGraveyard =
                        shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                rosterDay.getId(), GRAVEYARD);

                int graveyardRemaining =
                        graveyardRequired - assignedPerShift.getOrDefault(GRAVEYARD, 0);

                // 🚨 HARD GRAVEYARD RESERVATION
                long remainingMalePool =
                        employees.stream()
                                .filter(e -> !assignedToday.contains(e.getId()))
                                .filter(e -> e.getGender() != Gender.FEMALE)
                                .count();
                int nightRemaining =
                        getRequired(shiftConfigs, NIGHT)
                                - assignedPerShift.getOrDefault(NIGHT, 0);

                int totalNightFamilyRemaining =
                        Math.max(0, nightRemaining)
                                + Math.max(0, graveyardRemaining);

// 🚨 HARD NIGHT-FAMILY RESERVATION
                boolean nightFamilyCritical =
                        totalNightFamilyRemaining > 0;

                boolean malePoolTooTight =
                        remainingMalePool <= totalNightFamilyRemaining;

//                if (sc != NIGHT && sc != GRAVEYARD
//                        && nightFamilyCritical
//                        && malePoolTooTight
//                ) {
//                    continue;
//                }

                //Commented from loc 152 - New Addition - 2 - replacement

                if (!isWeekend &&
                        sc != NIGHT && sc != GRAVEYARD
                        && nightFamilyCritical
                        && malePoolTooTight) {
                    continue;
                }



//                if (sc != EVENING && sc != EARLY_MORNING && eveningRemaining > 0) {
//
//                    int safetyBuffer = 0;
//
//                    boolean eveningAtRisk =
//                            (sc != EARLY_MORNING) &&
//                                    (remainingEmployees <= (eveningRemaining - 1));
//                    if (eveningAtRisk) {
//                        continue;
//                    }
//                }

                //Commented from line 170 - if condition replaced
                //New Addition - 3




                if (!isWeekend &&
                        sc != EVENING &&
                        sc != EARLY_MORNING &&
                        eveningRemaining > 0) {

                    int safetyBuffer = 0;

                    boolean eveningAtRisk =
                            (sc != EARLY_MORNING) &&
                                    (remainingEmployees <= (eveningRemaining - 1));
                    if (eveningAtRisk) {
                        continue;
                    }
                }

                boolean protectGraveyard =
                        (sc != GRAVEYARD
                                && eveningRemaining == 0
                                && graveyardRemaining > 0
                                && remainingEmployees <= graveyardRemaining );

                if (protectGraveyard) continue;


                // =====================================================
// 🚨 EARLY MORNING PROTECTION
// Prevents graveyard from consuming early candidates
// =====================================================

                int earlyRequired =
                        getRequired(shiftConfigs, EARLY_MORNING);

                int earlyRemaining =
                        earlyRequired - assignedPerShift.getOrDefault(EARLY_MORNING, 0);

                boolean protectEarly =
                        (sc == GRAVEYARD
                                && earlyRemaining > 0
                                && remainingEmployees <= earlyRemaining);

                // if (protectEarly) continue;

                //Commented line no 228 replaced by
                //New Addition - 4

                if (!isWeekend && protectEarly) continue;

                long weeklyHours =
                        shiftAssignmentRepository.sumWeeklyHours(emp.getId(), weekId);

                // ⭐⭐⭐ FIX #1 — allow night family to exceed weekly cap
                boolean isNightFamily =
                        (sc == NIGHT || sc == GRAVEYARD);

                if (!isNightFamily && weeklyHours >= emp.getMaxWeeklyHours()) {
                    continue;
                }

                int nightRequired = getRequired(shiftConfigs, NIGHT);
                int graveRequired = getRequired(shiftConfigs, GRAVEYARD);

                long remainingNightEligible =
                        employees.stream()
                                .filter(e -> !assignedToday.contains(e.getId()))
                                .filter(e -> e.getGender() != Gender.FEMALE)
                                .count();

                int nightDemandLeft =
                        (nightRequired - assignedPerShift.getOrDefault(NIGHT, 0))
                                + (graveRequired - assignedPerShift.getOrDefault(GRAVEYARD, 0));

//                if (sc != NIGHT && sc != GRAVEYARD
//                        && remainingNightEligible <= nightDemandLeft) {
//                    continue;
//                }
                // =====================================================
// 🚨 GRAVEYARD → EVENING → EARLY PROTECTION
// =====================================================
                List<ShiftCode> recent =
                        shiftAssignmentRepository.findRecentShiftCodesBeforeDate(
                                emp.getId(),
                                rosterDay.getDayDate(),
                                org.springframework.data.domain.PageRequest.of(0, 2)
                        );

                if (recent.size() >= 2) {

                    ShiftCode yesterday = recent.get(0);
                    ShiftCode twoDaysAgo = recent.get(1);

                    if (twoDaysAgo == GRAVEYARD
                            && yesterday == EVENING
                            && sc == EARLY_MORNING) {

                        continue; // block unsafe pattern
                    }
                }
//                long recentGraveyards =
//                        shiftAssignmentRepository.countRecentShiftType(
//                                emp.getId(),
//                                GRAVEYARD,
//                                rosterDay.getDayDate(),
//                                rosterDay.getDayDate().minusDays(7)
//                        );

                if (sc == GRAVEYARD) {

                    long liveCurrent =
                            shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                    rosterDay.getId(), GRAVEYARD);

                    boolean lastSlot = liveCurrent >= graveyardRequired - 1;

                    long recentGraveyards =
                            shiftAssignmentRepository.countRecentShiftType(
                                    emp.getId(),
                                    GRAVEYARD,
                                    rosterDay.getDayDate(),
                                    rosterDay.getDayDate().minusDays(7)
                            );

                    // 🔥 NEW — check recovery (W/O)
                    boolean hadRecentWO =
                            weeklyOffRepository.existsByEmployee_IdAndOffDateBetween(
                                    emp.getId(),
                                    rosterDay.getDayDate().minusDays(3),
                                    rosterDay.getDayDate().minusDays(1)
                            );

                    // 🔥 RELAXATION LOGIC — ALLOW IF RESTED (W/O)
                    if (recentGraveyards >= 3 && !hadRecentWO) {

                        boolean criticalShortage =
                                liveCurrent < graveyardRequired;

                        if (!criticalShortage) {
                            continue;
                        }
                    }
                }


                RosterContext ctx =
                        contextBuilder.build(emp, rosterDay, config.getShiftType());

                if (ctx.isWeeklyOff()) continue;

                if (ctx.getConsecutiveNightCount() >= 6) {
                    if (sc == NIGHT || sc == GRAVEYARD) continue;
                }

                if ((sc == NIGHT || sc == GRAVEYARD)
                        && emp.getGender() == Gender.FEMALE)
                    continue;

                try {
                    validationService.validate(ctx);
                    assignmentService.assign(ctx);

                    assignedToday.add(emp.getId());
                    assignedPerShift.put(sc, current + 1);

                } catch (BusinessRuleException ex) {
                    log.debug("Assignment blocked → emp={} shift={} reason={}",
                            emp.getEmployeeCode(), sc, ex.getMessage());
                }
            }
        }
        performEarlyRecovery(rosterDay, assignedToday);
        performNightRecovery(rosterDay, assignedToday);

        performFinalBackfillEveningFirst(rosterDay, employees, assignedToday);

        performCrossShiftRebalance(rosterDay, assignedToday);

        //did after almost stable
        performCriticalOnDutyToGraveyard(rosterDay, assignedToday);
        performEveningLastRescue(rosterDay, assignedToday);

        //Final addition - 1

//        performCriticalOnDutyToGraveyard(rosterDay, assignedToday);


        performOnDutyBackfill(rosterDay, employees, assignedToday);

    }
    // =====================================================
    // FINAL BACKFILL — WITH HARD EVENING GUARD
    // =====================================================
    private void performFinalBackfillEveningFirst(
            RosterDay day,
            List<Employee> employees,
            Set<Long> assignedToday) {

        List<ShiftConfig> configs =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                day.getRosterWeek().getId(),
                                day.getDayCategory());

        configs.sort((a, b) -> {

            ShiftCode sa = a.getShiftType().getCode();
            ShiftCode sb = b.getShiftType().getCode();

            long needA =
                    a.getRequiredResources()
                            - shiftAssignmentRepository
                            .countByRosterDayAndShiftCode(day.getId(), sa);

            long needB =
                    b.getRequiredResources()
                            - shiftAssignmentRepository
                            .countByRosterDayAndShiftCode(day.getId(), sb);

            return Long.compare(needB, needA);
        });

        int eveningRequired = getRequired(configs, EVENING);

        for (ShiftConfig cfg : configs) {

            ShiftCode sc = cfg.getShiftType().getCode();
            if (sc == ON_DUTY) continue;

            long eveningNow =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(), EVENING);

            boolean protectEvening =
                    (sc != EVENING
                            && sc != GRAVEYARD
                            && eveningNow < eveningRequired);

            if (protectEvening) {
                continue;
            }
            int required = cfg.getRequiredResources();

            long current =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(), sc);

            if (current >= required) continue;

            for (Employee e : employees) {

                boolean alreadyAssignedInDb =
                        shiftAssignmentRepository
                                .existsByEmployee_IdAndRosterDay_Id(
                                        e.getId(),
                                        day.getId());

                if (alreadyAssignedInDb || assignedToday.contains(e.getId())) {
                    continue;
                }

                try {
                    RosterContext ctx =
                            contextBuilder.build(e, day, cfg.getShiftType());


                    //newly added

                    // ================= FAIRNESS GUARD (SOFT) =================

// avoid overloading same shift repeatedly
                    long recentSameShiftCount =
                            shiftAssignmentRepository.countRecentShiftType(
                                    e.getId(),
                                    sc,
                                    day.getDayDate(),
                                    day.getDayDate().minusDays(21)
                            );

// 🚨 DO NOT block critical shortage situations
                    boolean criticalShift =
                            (sc == NIGHT || sc == GRAVEYARD || sc == EVENING);

// soft skip only if not critical and not dragged
                    if (!criticalShift
                            && !ctx.isDraggedOverride()
                            && recentSameShiftCount >= 8) {

                        continue; // try next employee first
                    }

                    validationService.validateHard(ctx);
                    assignmentService.assign(ctx);

                    assignedToday.add(e.getId());
                    current++;

                    if (current >= required) break;

                } catch (BusinessRuleException ignored) {
                }
            }
        }
    }

    // =====================================================
    // EARLY RECOVERY — PREVIOUS DAY EVENING
    // =====================================================
    private void performEarlyRecovery(RosterDay day, Set<Long> assignedToday) {

        int required =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                day.getRosterWeek().getId(),
                                day.getDayCategory(),
                                EARLY_MORNING)
                        .map(ShiftConfig::getRequiredResources)
                        .orElse(0);

        long current =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                        day.getId(), EARLY_MORNING);

        if (current >= required) return;

        LocalDate prevDate = day.getDayDate().minusDays(1);

        List<Employee> eveningEmployees =
                shiftAssignmentRepository
                        .findEveningAssignedEmployeesByDate(prevDate)
                        .stream()
                        .filter(e -> !assignedToday.contains(e.getId()))
                        .sorted(Comparator.comparingLong(
                                e -> shiftAssignmentRepository.sumWeeklyHours(
                                        e.getId(),
                                        day.getRosterWeek().getId()
                                )
                        ))
                        .toList();

        ShiftType early = getShiftType(day, EARLY_MORNING);

        for (Employee e : eveningEmployees) {

            if (current >= required) break;

            try {
                assignmentService.assignDragged(e, day, early);
                assignedToday.add(e.getId());
                current++;

            } catch (BusinessRuleException ignored) {
            }
        }

        // =====================================================
// 🔥 SECONDARY EARLY FALLBACK — SAFE STABILIZER
// =====================================================
        if (current < required) {

            log.warn("⚠️ Early still short after evening drag → activating fallback");

            List<Employee> fallbackPool =
                    employeeRepository.findActiveNotOnLeave(day.getDayDate())
                            .stream()
                            .filter(e -> !assignedToday.contains(e.getId()))
                            // ✅ DO NOT gender filter — Early allows females
                            .sorted(Comparator.comparingLong(
                                    e -> shiftAssignmentRepository.sumWeeklyHours(
                                            e.getId(),
                                            day.getRosterWeek().getId())))
                            .toList();

            for (Employee e : fallbackPool) {

                if (current >= required) break;

                try {
                    assignmentService.assignDragged(e, day, early);
                    assignedToday.add(e.getId());
                    current++;

                    log.warn("🔧 Early fallback filled using emp={}",
                            e.getEmployeeCode());

                } catch (BusinessRuleException ignored) {
                }
            }
        }



    }
    // =====================================================
    private void performNightRecovery(RosterDay day, Set<Long> assignedToday) {

        List<ShiftCode> nightOrder =
                List.of(NIGHT, GRAVEYARD);

        for (ShiftCode code : nightOrder) {

            int required = requiredFor(day, code);

            long current =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(), code);

            if (current >= required) continue;

            ShiftCode sibling =
                    (code == NIGHT) ? GRAVEYARD : NIGHT;

            int siblingRequired = requiredFor(day, sibling);

            List<Employee> candidates =
                    employeeRepository.findActiveNotOnLeave(day.getDayDate())
                            .stream()
                            .filter(e -> !assignedToday.contains(e.getId()))
                            .filter(e -> e.getGender() != Gender.FEMALE)
                            .sorted(
                                    Comparator
                                            .comparingLong((Employee e) ->
                                                    shiftAssignmentRepository.sumWeeklyHours(
                                                            e.getId(),
                                                            day.getRosterWeek().getId()))
                            )
                            .toList();

            for (Employee e : candidates) {

                if (current >= required) break;

                try {

                    // ===== NIGHT PROTECTION =====
                    if (code == NIGHT) {

                        long liveGrave =
                                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                        day.getId(), GRAVEYARD);

                        // block only if graveyard fully empty
                        if (liveGrave == 0 && siblingRequired > 2) {
                            continue;
                        }
                    }

                    // ===== GRAVEYARD PROTECTION =====
                    if (code == GRAVEYARD) {

                        long liveNight =
                                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                        day.getId(), NIGHT);

                        int nightRequired = requiredFor(day, NIGHT);

                        // secure Night first
                        if (liveNight < nightRequired) {
                            continue;
                        }
                    }

                    long liveCurrent =
                            shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                    day.getId(), code);

                    if (liveCurrent >= required) {
                        break;
                    }

                    RosterContext ctx =
                            contextBuilder.build(
                                            e,
                                            day,
                                            getShiftType(day, code))
                                    .toBuilder()
                                    .draggedOverride(true)
                                    .build();

                    validationService.validateHard(ctx);
                    assignmentService.assign(ctx);

                    assignedToday.add(e.getId());

                    current =
                            shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                    day.getId(), code);

                } catch (BusinessRuleException ignored) {
                }
            }

            // ===== FALLBACK =====
            if (current < required) {

                List<Employee> fallback =
                        employeeRepository.findActiveNotOnLeave(day.getDayDate())
                                .stream()
                                .filter(e -> !assignedToday.contains(e.getId()))
                                .filter(e -> e.getGender() != Gender.FEMALE)
                                .sorted(
                                        Comparator.comparingLong(
                                                e -> shiftAssignmentRepository.sumWeeklyHours(
                                                        e.getId(),
                                                        day.getRosterWeek().getId()))
                                )
                                .toList();

                for (Employee e : fallback) {

                    if (current >= required) break;

                    try {

                        long liveCurrent =
                                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                        day.getId(), code);

                        if (liveCurrent >= required) {
                            break;
                        }

                        RosterContext ctx =
                                contextBuilder.build(
                                                e,
                                                day,
                                                getShiftType(day, code))
                                        .toBuilder()
                                        .draggedOverride(true)
                                        .build();

                        validationService.validateHard(ctx);
                        assignmentService.assign(ctx);

                        assignedToday.add(e.getId());

                        current =
                                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                        day.getId(), code);

                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }




    // =====================================================
    // HELPER METHODS (UNCHANGED)
    // =====================================================

    private int getRequired(List<ShiftConfig> configs, ShiftCode code) {
        return configs.stream()
                .filter(c -> c.getShiftType().getCode() == code)
                .findFirst()
                .map(ShiftConfig::getRequiredResources)
                .orElse(0);
    }

    private ShiftType getShiftType(RosterDay day, ShiftCode code) {
        if (code == ON_DUTY) {
            return shiftTypeRepository
                    .findByCode(ON_DUTY)
                    .orElseThrow(() -> new RuntimeException("ON_DUTY shift type missing"));
        }

        return shiftConfigRepository
                .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                        day.getRosterWeek().getId(),
                        day.getDayCategory(),
                        code)
                .orElseThrow()
                .getShiftType();
    }



    private void performEveningLastRescue(
            RosterDay day,
            Set<Long> assignedToday) {

        int eveningRequired =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                day.getRosterWeek().getId(),
                                day.getDayCategory(),
                                EVENING)
                        .map(ShiftConfig::getRequiredResources)
                        .orElse(0);

        long eveningCurrent =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                        day.getId(), EVENING);

        if (eveningCurrent >= eveningRequired) {
            return; // already fine
        }

        int shortage = eveningRequired - (int) eveningCurrent;

        log.warn("Evening LAST RESCUE triggered → shortage={}", shortage);

        List<Employee> candidates =
                employeeRepository.findActiveNotOnLeave(day.getDayDate())
                        .stream()
                        .filter(e ->
                                !shiftAssignmentRepository
                                        .existsByEmployee_IdAndRosterDay_Id(
                                                e.getId(),
                                                day.getId()))
                        .sorted(Comparator.comparingLong(
                                e -> shiftAssignmentRepository.sumWeeklyHours(
                                        e.getId(),
                                        day.getRosterWeek().getId())))
                        .toList();
        ShiftType eveningType = getShiftType(day, EVENING);

        for (Employee e : candidates) {

            if (shortage <= 0) break;

            boolean alreadyAssignedInDb =
                    shiftAssignmentRepository
                            .existsByEmployee_IdAndRosterDay_Id(
                                    e.getId(),
                                    day.getId());

            if (alreadyAssignedInDb || assignedToday.contains(e.getId())) {
                continue;
            }

            try {
                RosterContext ctx =
                        contextBuilder.build(e, day, eveningType)
                                .toBuilder()
                                .draggedOverride(true)
                                .build();

                long weeklyHours =
                        shiftAssignmentRepository.sumWeeklyHours(
                                e.getId(),
                                day.getRosterWeek().getId());

                if (weeklyHours >= e.getMaxWeeklyHours()) {
                    continue;
                }



                validationService.validateHard(ctx);
                assignmentService.assign(ctx);
                assignedToday.add(e.getId());
                shortage--;

            } catch (BusinessRuleException ignored) {
            }
        }

    }
    private void performOnDutyBackfill(
            RosterDay day,
            List<Employee> employees,
            Set<Long> assignedToday) {


        // do not assign ON_DUTY if any critical shift is still short
        int graveReq = requiredFor(day, GRAVEYARD);
        long graveNow =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), GRAVEYARD);

        int earlyReq = requiredFor(day, EARLY_MORNING);
        long earlyNow =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), EARLY_MORNING);

        int eveningReq = requiredFor(day, EVENING);
        long eveningNow =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), EVENING);
//
//        if (graveNow < graveReq || earlyNow < earlyReq || eveningNow < eveningReq) {
//            return;
//        }

        int nightReq = requiredFor(day, NIGHT);

        long nightNow =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), NIGHT);

        if (graveNow < graveReq ||
                earlyNow < earlyReq ||
                eveningNow < eveningReq ||
                nightNow < nightReq) {
            return;
        }



        int remainingEmployees =
                employees.size() - assignedToday.size();

        int totalCriticalRemaining =
                Math.max(0, graveReq - (int)graveNow)
                        + Math.max(0, earlyReq - (int)earlyNow)
                        + Math.max(0, eveningReq - (int)eveningNow)
                        + Math.max(0, nightReq - (int)nightNow);

// 🚨 DO NOT consume last critical pool
        if (remainingEmployees <= totalCriticalRemaining) {
            return;
        }

        ShiftType onDutyType = getShiftType(day, ON_DUTY);
        Long weekId = day.getRosterWeek().getId();

        List<Employee> ordered = employees.stream()
                .filter(e -> !assignedToday.contains(e.getId()))
                .sorted(
                        Comparator
                                // ✅ PRIMARY — fewer ON-DUTY this week
                                .comparingLong((Employee e) ->
                                        shiftAssignmentRepository.countOnDutyInWeek(
                                                e.getId(),
                                                weekId))

                                // ✅ SECONDARY — fewer weekly hours
                                .thenComparingLong(e ->
                                        shiftAssignmentRepository.sumWeeklyHours(
                                                e.getId(),
                                                weekId))
                )
                .toList();

        for (Employee e : ordered) {

            long weeklyHours =
                    shiftAssignmentRepository.sumWeeklyHours(
                            e.getId(),
                            weekId
                    );

            int remaining =
                    (int) (e.getMaxWeeklyHours() - weeklyHours);

            if (remaining <= 0) continue;


            // 🚨 HARD WEEKLY ON-DUTY LIMIT
            long onDutyThisWeek =
                    shiftAssignmentRepository.countOnDutyInWeek(
                            e.getId(),
                            weekId
                    );

            if (onDutyThisWeek >= 1) {
                continue;
            }
            // =====================================================
            // ⭐⭐⭐ NEW: ON-DUTY STREAK PROTECTION (SOFT) ⭐⭐⭐
            // =====================================================

            List<ShiftCode> recent =
                    shiftAssignmentRepository.findRecentShiftCodesBeforeDate(
                            e.getId(),
                            day.getDayDate(),
                            org.springframework.data.domain.PageRequest.of(0, 2)
                    );
            // 🚨 PROTECT NIGHT FAMILY EMPLOYEES
            boolean recentNightFamily =
                    recent.stream().anyMatch(s ->
                            s == NIGHT || s == GRAVEYARD);

            if (recentNightFamily) {
                continue;
            }

            boolean hadOnDutyYesterday =
                    !recent.isEmpty() && recent.get(0) == ON_DUTY;

            long recentOnDutyCount =
                    shiftAssignmentRepository.countRecentShiftType(
                            e.getId(),
                            ON_DUTY,
                            day.getDayDate(),
                            day.getDayDate().minusDays(14)
                    );

            // 🚨 SOFT skip — prevents streaks but won't create blanks
            if (hadOnDutyYesterday && recentOnDutyCount >= 1) {
                continue;
            }

            // =====================================================

            int hoursToAssign =
                    Math.min(onDutyType.getShiftHours(), remaining);

            try {

                RosterContext ctx =
                        contextBuilder.build(e, day, onDutyType)
                                .toBuilder()
                                .actualHours(hoursToAssign)
                                .draggedOverride(true)
                                .build();

                validationService.validateHard(ctx);
                assignmentService.assign(ctx);

                assignedToday.add(e.getId());

                log.debug("ON_DUTY fair-fill → emp={} recentCount={}",
                        e.getEmployeeCode(),
                        recentOnDutyCount);

            } catch (BusinessRuleException ignored) {
            }
        }
    }
    private void performGlobalShortageSweep(
            RosterDay day,
            List<Employee> employees,
            Set<Long> assignedToday) {

        List<ShiftConfig> configs =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                day.getRosterWeek().getId(),
                                day.getDayCategory());

        for (ShiftConfig cfg : configs) {

            ShiftCode sc = cfg.getShiftType().getCode();
            if (sc == ON_DUTY) continue;

            int required = cfg.getRequiredResources();

            long current =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(), sc);

            if (current >= required) continue;

            int shortage = required - (int) current;

            log.error("GLOBAL SWEEP → {} still short by {}", sc, shortage);

            ShiftType targetType = cfg.getShiftType();

            // 🔥 last resort pool = lowest weekly hours employees
            List<Employee> pool = employees.stream()
                    .sorted(Comparator.comparingLong(
                            e -> shiftAssignmentRepository.sumWeeklyHours(
                                    e.getId(),
                                    day.getRosterWeek().getId())))
                    .toList();

            for (Employee e : pool) {

                if (shortage <= 0) break;

                try {
                    RosterContext ctx =
                            contextBuilder.build(e, day, targetType)
                                    .toBuilder()
                                    .draggedOverride(true)
                                    .build();

                    validationService.validateHard(ctx);
                    assignmentService.assign(ctx);

                    assignedToday.add(e.getId());
                    shortage--;

                    log.warn("GLOBAL SWEEP filled {} using emp={}",
                            sc, e.getEmployeeCode());

                } catch (BusinessRuleException ignored) {
                }
            }
        }
    }
    // =====================================================
// 🔥 CROSS SHIFT REBALANCE — FINAL STABILIZER
// Fixes last-slot shortages when pool becomes tight
// =====================================================
    private void performCrossShiftRebalance(
            RosterDay day,
            Set<Long> assignedToday) {

        Long weekId = day.getRosterWeek().getId();

        List<ShiftConfig> configs =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndActiveTrue(
                                weekId,
                                day.getDayCategory());

        // ---- find underfilled shifts first
        for (ShiftConfig targetCfg : configs) {

            ShiftCode targetCode = targetCfg.getShiftType().getCode();

            if (targetCode == ON_DUTY) continue;

            int required = targetCfg.getRequiredResources();

            long current =
                    shiftAssignmentRepository.countByRosterDayAndShiftCode(
                            day.getId(),
                            targetCode);

            if (current >= required) continue;

            int shortage = required - (int) current;

            log.warn("Cross rebalance triggered for {} shortage={}",
                    targetCode, shortage);

            List<ShiftConfig> donorOrder = new ArrayList<>(configs);

            // prioritize EVENING for GRAVEYARD
//            if (targetCode == GRAVEYARD) {
//                donorOrder.sort((a, b) -> {
//                    if (a.getShiftType().getCode() == EVENING) return -1;
//                    if (b.getShiftType().getCode() == EVENING) return 1;
//                    return 0;
//                });
//            }

            for (ShiftConfig donorCfg : donorOrder) {

                ShiftCode donorCode = donorCfg.getShiftType().getCode();

                if (donorCode == targetCode) continue;
                if (donorCode == ON_DUTY) continue;
                if (donorCode == NIGHT) continue;
                if (donorCode == GRAVEYARD) continue;
                if (donorCode == EARLY_MORNING) continue;


                int donorRequired = donorCfg.getRequiredResources();

                long donorCurrent =
                        shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                day.getId(),
                                donorCode);

                //int surplus = (int) donorCurrent - donorRequired;

//                boolean canBorrowFromEvening =
//                        (targetCode == GRAVEYARD && donorCode == EVENING);

                boolean canBorrowFromEvening = false;

                // 🔥 EVENING IS ALWAYS A DONOR POOL
                if (!canBorrowFromEvening) {
                    int surplus = (int) donorCurrent - donorRequired;
                    if (surplus <= 0) continue;
                }

//                if (surplus <= 0 && !canBorrowFromEvening) {
//                    continue;
//                }

                // allow controlled drop from evening
                if (canBorrowFromEvening) {

                    int maxAllowedDrop = 1;
                    long minAllowed = donorRequired - maxAllowedDrop;

                    if (donorCurrent <= minAllowed) {
                        continue;
                    }
                }

                // ---- pick weakest employee from donor shift
                List<Employee> donors =
                        shiftAssignmentRepository
                                .findEmployeesByShiftCodeAndDate(
                                        donorCode,
                                        day.getDayDate())
                                .stream()
                                .sorted(Comparator.comparingLong(
                                        e -> shiftAssignmentRepository
                                                .sumWeeklyHours(e.getId(), weekId)))
                                .toList();

                ShiftType targetType = targetCfg.getShiftType();

                for (Employee emp : donors) {

                    if (shortage <= 0) break;

                    try {

                        long liveTargetNow =
                                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                                        day.getId(), targetCode);

                        if (liveTargetNow >= required) {
                            break;
                        }

                        // 🚨 GRAVEYARD fatigue protection (relaxed if shortage)
                        if (targetCode == GRAVEYARD) {

                            long recentGraveyards =
                                    shiftAssignmentRepository.countRecentShiftType(
                                            emp.getId(),
                                            GRAVEYARD,
                                            day.getDayDate(),
                                            day.getDayDate().minusDays(7)
                                    );

                            boolean criticalShortage = liveTargetNow < required;

                            boolean hadRecentWO =
                                    weeklyOffRepository.existsByEmployee_IdAndOffDateBetween(
                                            emp.getId(),
                                            day.getDayDate().minusDays(3),
                                            day.getDayDate().minusDays(1)
                                    );

                            if (!criticalShortage && recentGraveyards >= 3 && !hadRecentWO) {
                                continue;
                            }
                        }

                        // ===============================
                        // 🔥 CRITICAL FIX — MOVE SHIFT
                        // ===============================



                        // 2️⃣ ASSIGN to target shift
                        RosterContext ctx =
                                contextBuilder.build(emp, day, targetType)
                                        .toBuilder()
                                        .draggedOverride(true)
                                        .build();

                        validationService.validateHard(ctx);
                        // 1️⃣ REMOVE from donor shift
                        shiftAssignmentRepository
                                .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                        emp.getId(),
                                        day.getId(),
                                        donorCode
                                );
                        assignmentService.assign(ctx);

                        assignedToday.add(emp.getId());
                        shortage--;
//                        surplus--;

                        log.warn("Rebalanced {} from {} → {}",
                                emp.getEmployeeCode(),
                                donorCode,
                                targetCode);

//                        if (surplus <= 0) break;

                    } catch (BusinessRuleException ignored) {
                    }
                }

                if (shortage <= 0) break;
            }
        }
    }
    private void performFinalSafetySweep(
            RosterDay day,
            List<Employee> employees,Set<Long> assignedToday) {


        boolean graveOk =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), GRAVEYARD)
                        >= requiredFor(day, GRAVEYARD);

        boolean earlyOk =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), EARLY_MORNING)
                        >= requiredFor(day, EARLY_MORNING);

        boolean eveningOk =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), EVENING)
                        >= requiredFor(day, EVENING);

        boolean nightOk =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), NIGHT)
                        >= requiredFor(day, NIGHT);

// 🚨 BLOCK ON-DUTY if ANY shift is underfilled
        if (!graveOk || !earlyOk || !eveningOk || !nightOk) {
            return;
        }


        ShiftType onDutyType = getShiftType(day, ON_DUTY);
        long totalOnDuty =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(day.getId(), ON_DUTY);

        if (totalOnDuty >= 1) {
            return; // 🚨 hard cap: max 1 ON_DUTY per day
        }
        for (Employee e : employees) {

            boolean alreadyAssigned =
                    shiftAssignmentRepository
                            .existsByEmployee_IdAndRosterDay_Id(
                                    e.getId(), day.getId());

            if (alreadyAssigned || assignedToday.contains(e.getId())) continue;

            long onDutyThisWeek =
                    shiftAssignmentRepository.countOnDutyInWeek(
                            e.getId(),
                            day.getRosterWeek().getId());

            if (onDutyThisWeek >= 1) {
                continue;
            }
            try {
                assignmentService.assignDragged(e, day, onDutyType);
                assignedToday.add(e.getId()); // 🔥 MUST ADD

                log.warn("FINAL SAFETY filled → emp={}",
                        e.getEmployeeCode());

            } catch (Exception ignored) {
            }
        }
    }
    private int requiredFor(RosterDay day, ShiftCode code) {
        return shiftConfigRepository
                .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                        day.getRosterWeek().getId(),
                        day.getDayCategory(),
                        code)
                .map(ShiftConfig::getRequiredResources)
                .orElse(0);
    }
    private void performCriticalGraveyardFill(
            RosterDay day,
            Set<Long> assignedToday) {

        int required = requiredFor(day, GRAVEYARD);

        long current =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                        day.getId(), GRAVEYARD);

        if (current >= required) return;

        int shortage = required - (int) current;

        log.error("🔥 CRITICAL GRAVEYARD FILL → shortage={}", shortage);

        List<Employee> eveningPool =
                shiftAssignmentRepository
                        .findEmployeesByShiftCodeAndDate(EVENING, day.getDayDate());

        for (Employee emp : eveningPool) {

            if (shortage <= 0) break;

            try {

                // ✅ REMOVE FIRST
                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                emp.getId(),
                                day.getId(),
                                EVENING
                        );
                shiftAssignmentRepository.flush(); // 🔥 ADD THIS LINE
                // ✅ BUILD CONTEXT AFTER REMOVE
                RosterContext ctx =
                        contextBuilder.build(emp, day, getShiftType(day, GRAVEYARD))
                                .toBuilder()
                                .draggedOverride(true)
                                .build();

                // ✅ VALIDATE
                validationService.validateHard(ctx);

                // ✅ ASSIGN
                assignmentService.assign(ctx);

                assignedToday.add(emp.getId()); // ✅ IMPORTANT

                shortage--;

                log.error("🔥 FORCED MOVE EVENING → GRAVEYARD emp={}",
                        emp.getEmployeeCode());

            } catch (Exception ex) {

                log.error("CRITICAL FILL FAILED → emp={} reason={}",
                        emp.getEmployeeCode(),
                        ex.getMessage());
            }
        }
    }

    //Final addition - 2

    private void performCriticalOnDutyToGraveyard(
            RosterDay day,
            Set<Long> assignedToday) {

        int required = requiredFor(day, GRAVEYARD);

        long current =
                shiftAssignmentRepository.countByRosterDayAndShiftCode(
                        day.getId(), GRAVEYARD);

        if (current >= required) return;

        int shortage = required - (int) current;

        if (shortage != 1) return;

        List<Employee> onDutyPool =
                shiftAssignmentRepository
                        .findEmployeesByShiftCodeAndDate(
                                ON_DUTY,
                                day.getDayDate());

        for (Employee emp : onDutyPool) {

            try {

                shiftAssignmentRepository
                        .deleteByEmployeeAndRosterDayAndShiftType_Code(
                                emp.getId(),
                                day.getId(),
                                ON_DUTY
                        );

                RosterContext ctx =
                        contextBuilder.build(
                                        emp,
                                        day,
                                        getShiftType(day, GRAVEYARD))
                                .toBuilder()
                                .draggedOverride(true)
                                .build();

                validationService.validateHard(ctx);
                assignmentService.assign(ctx);

                assignedToday.add(emp.getId());

                return;

            } catch (Exception ignored) {
            }
        }
    }

}
package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.*;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.RosterStatus;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RosterSetupService {

    private final RosterWeekRepository rosterWeekRepository;
    private final RosterDayRepository rosterDayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftConfigRepository shiftConfigRepository;



    public RosterWeek createWeek(LocalDate weekStartDate, boolean copyPrevious) {

        if (rosterWeekRepository.findByWeekStartDate(weekStartDate).isPresent()) {
            throw new RuntimeException("Roster week already exists");
        }

        // 1️⃣ Create new week
        RosterWeek week = RosterWeek.builder()
                .weekStartDate(weekStartDate)
                .weekEndDate(weekStartDate.plusDays(6))
                .published(false)
                .rosterStatus(RosterStatus.DRAFT)
                .build();

        rosterWeekRepository.save(week);

        // 2️⃣ Create days
        createRosterDays(week);

        // 3️⃣ COPY PREVIOUS CONFIG (if enabled)
        if (copyPrevious) {

            List<RosterWeek> allWeeks =
                    rosterWeekRepository.findAll()
                            .stream()
                            .sorted((a, b) ->
                                    b.getWeekStartDate().compareTo(a.getWeekStartDate()))
                            .toList();

            RosterWeek prevWeek = null;

            for (RosterWeek w : allWeeks) {
                if (!w.getWeekStartDate().equals(weekStartDate)) {
                    prevWeek = w;
                    break;
                }
            }

            if (prevWeek != null) {

                List<ShiftConfig> oldConfigs =
                        shiftConfigRepository.findByRosterWeek_Id(prevWeek.getId());

                for (ShiftConfig old : oldConfigs) {

                    ShiftConfig newCfg = ShiftConfig.builder()
                            .rosterWeek(week)
                            .dayCategory(old.getDayCategory())
                            .shiftType(old.getShiftType())
                            .requiredResources(old.getRequiredResources())
                            .active(true)
                            .build();

                    shiftConfigRepository.save(newCfg);
                }
            }
        }

        return week;
    }
    private void createRosterDays(RosterWeek week) {

        for (int i = 0; i < 7; i++) {

            LocalDate date = week.getWeekStartDate().plusDays(i);

            DayCategory category =
                    (date.getDayOfWeek().getValue() >= 6)
                            ? DayCategory.WEEKEND
                            : DayCategory.WEEKDAY;

            RosterDay day = RosterDay.builder()
                    .rosterWeek(week)
                    .dayDate(date)
                    .dayCategory(category)
                    .build();

            rosterDayRepository.save(day);
        }
    }
}
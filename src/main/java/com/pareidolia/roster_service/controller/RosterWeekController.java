package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.dto.CreateRosterWeekDto;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.repository.RosterWeekRepository;
import com.pareidolia.roster_service.service.RosterSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/roster-weeks")
@RequiredArgsConstructor
public class RosterWeekController {

    private final RosterSetupService rosterSetupService;
    private final RosterWeekRepository repo;


    @GetMapping
    public List<RosterWeek> getAllWeeks() {
        return repo.findAll();
    }
    @PostMapping
    public RosterWeek createWeek(
            @RequestParam LocalDate weekStartDate,
            @RequestParam(defaultValue = "true") boolean copyPrevious
    ) {
        return rosterSetupService.createWeek(weekStartDate, copyPrevious);
    }

    @GetMapping("/by-start/{weekStart}")
    public RosterWeek getByStartDate(
            @PathVariable LocalDate weekStart) {

        return repo.findByWeekStartDate(weekStart)
                .orElseThrow(() -> new RuntimeException("Week not found"));
    }
}
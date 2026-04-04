package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.dto.RosterRequestDto;
import com.pareidolia.roster_service.dto.RosterResponseDto;
import com.pareidolia.roster_service.mapper.RosterMapper;
import com.pareidolia.roster_service.service.RosterGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/roster")
@RequiredArgsConstructor
public class RosterController {

    private final RosterGenerationService rosterGenerationService;
    private final RosterMapper rosterMapper;

    /**
     * Generate roster for a full week
     */
    @PostMapping("/generate")
    public void generateRoster(@Valid @RequestBody RosterRequestDto dto) {
        rosterGenerationService.generateForWeek(dto.getWeekStartDate());
    }

    /**
     * Get roster for a single day
     */
    @GetMapping("/day/{date}")
    public List<RosterResponseDto> getRosterForDay(
            @PathVariable LocalDate date
    ) {
        return rosterGenerationService.getAssignmentsForDay(date)
                .stream()
                .map(rosterMapper::toDto)
                .toList();
    }

    /**
     * Get roster for a week
     */
    @GetMapping("/week/{weekId}")
    public List<RosterResponseDto> getRosterForWeek(
            @PathVariable Long weekId
    ) {
        return rosterGenerationService.getAssignmentsForWeek(weekId)
                .stream()
                .map(rosterMapper::toDto)
                .toList();
    }
}

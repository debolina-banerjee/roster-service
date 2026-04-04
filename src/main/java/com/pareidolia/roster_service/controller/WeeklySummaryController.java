package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.entity.WeeklySummary;
import com.pareidolia.roster_service.repository.WeeklySummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class WeeklySummaryController {
    private final WeeklySummaryRepository repo;

    @GetMapping("/week/{weekStart}")
    public List<WeeklySummary> getWeek(
            @PathVariable LocalDate weekStart) {
        return repo.findByWeekStart(weekStart);
    }

    @GetMapping("/employee/{empId}/{weekStart}")
    public WeeklySummary getOne(
            @PathVariable Long empId,
            @PathVariable LocalDate weekStart) {

        return repo.findByEmployeeIdAndWeekStart(empId, weekStart)
                .orElseThrow();
    }
}

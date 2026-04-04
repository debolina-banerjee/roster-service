package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.WeeklySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklySummaryRepository
        extends JpaRepository<WeeklySummary, Long> {

    Optional<WeeklySummary>
    findByEmployeeIdAndWeekStart(Long empId, LocalDate weekStart);

    List<WeeklySummary>
    findByWeekStart(LocalDate weekStart);


}
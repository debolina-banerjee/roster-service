package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.WeeklyOffPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WeeklyOffPreferenceRepository extends JpaRepository<WeeklyOffPreference, Long> {
    List<WeeklyOffPreference> findByWeekStartDate(LocalDate weekStartDate);
    int countByPreferredDate(LocalDate preferredDate);

    int countByPreferredDateAndWeekStartDate(
            LocalDate preferredDate,
            LocalDate weekStartDate
    );

    void deleteByWeekStartDate(LocalDate weekStartDate);
}

package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShiftConfigRepository extends JpaRepository<ShiftConfig, Long> {

    // ✅ WEEK-AWARE (PRIMARY)
    List<ShiftConfig> findByRosterWeek_IdAndDayCategoryAndActiveTrue(
            Long rosterWeekId,
            DayCategory dayCategory
    );

    // ✅ Used in fallbacks
    Optional<ShiftConfig> findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
            Long rosterWeekId,
            DayCategory dayCategory,
            ShiftCode shiftCode
    );

    // ✅ Optional helper
    @Query("""
        SELECT sc FROM ShiftConfig sc
        WHERE sc.active = true
          AND sc.rosterWeek.id = :weekId
    """)
    List<ShiftConfig> findActiveByWeek(Long weekId);


    List<ShiftConfig> findByRosterWeek_Id(Long weekId);


}
package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.RosterDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RosterDayRepository extends JpaRepository<RosterDay, Long> {

    List<RosterDay> findByRosterWeekId(Long rosterWeekId);

    Optional<RosterDay> findByRosterWeekIdAndDayDate(
            Long rosterWeekId,
            LocalDate date
    );
}
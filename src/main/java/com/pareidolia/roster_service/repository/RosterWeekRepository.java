package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.enumtype.RosterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RosterWeekRepository extends JpaRepository<RosterWeek, Long> {

    Optional<RosterWeek> findByWeekStartDate(LocalDate weekStartDate);

    List<RosterWeek> findByRosterStatus(RosterStatus status);


}
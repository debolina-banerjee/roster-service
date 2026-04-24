package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.WeeklyOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;



@Repository
public interface WeeklyOffRepository extends JpaRepository<WeeklyOff, Long> {

    boolean existsByEmployee_IdAndOffDate(
            Long employeeId,
            LocalDate offDate
    );

    Optional<WeeklyOff> findByEmployee_IdAndOffDate(
            Long employeeId,
            LocalDate offDate
    );

    List<WeeklyOff> findByEmployee_Id(Long employeeId);

    List<WeeklyOff> findByEmployee_IdAndWeekStartDate(
            Long employeeId,
            LocalDate weekStartDate
    );

    boolean existsByEmployee_IdAndOffDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<WeeklyOff> findFirstByEmployee_IdAndWeekStartDate(
            Long employeeId,
            LocalDate weekStartDate
    );

}



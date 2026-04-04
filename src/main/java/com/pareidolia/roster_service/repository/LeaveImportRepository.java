package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.LeaveImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveImportRepository
        extends JpaRepository<LeaveImport, Long> {

    Optional<LeaveImport> findByEmployee_IdAndLeaveDate(
            Long employeeId,
            LocalDate leaveDate
    );

    List<LeaveImport> findByLeaveDateBetween(
            LocalDate start,
            LocalDate end
    );

    // ✅ NEW METHOD – EXACT FIX
    @Query("""
    SELECT COUNT(l)
    FROM LeaveImport l
    WHERE l.employee.id = :empId
    AND l.leaveDate BETWEEN :weekStart AND :weekEnd
""")
    int countByEmployeeAndWeek(
            Long empId,
            LocalDate weekStart,
            LocalDate weekEnd
    );
}
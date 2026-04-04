package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.entity.ShiftAssignment;
import com.pareidolia.roster_service.enumtype.ShiftCode;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository
        extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findByRosterDay_Id(Long rosterDayId);

    List<ShiftAssignment> findByRosterDay_DayDate(LocalDate date);

    List<ShiftAssignment> findByRosterDay_RosterWeek_Id(Long rosterWeekId);

    // 🔥 HOURS BASED ON actualHours
    @Query("""
        SELECT COALESCE(SUM(sa.actualHours), 0)
        FROM ShiftAssignment sa
        WHERE sa.employee.id = :employeeId
        AND sa.rosterDay.rosterWeek.id = :rosterWeekId
    """)
    Long sumWeeklyHours(Long employeeId, Long rosterWeekId);

    @Query("""
        SELECT sa.employee
        FROM ShiftAssignment sa
        WHERE sa.rosterDay.id = :rosterDayId
        AND sa.shiftType.code = 'EVENING'
    """)
    List<Employee> findEveningAssignedEmployees(Long rosterDayId);

    // 🔥🔥🔥 CRITICAL FIX — ONLY SHIFTS BEFORE CURRENT DATE
    @Query("""
        SELECT sa.shiftType.code
        FROM ShiftAssignment sa
        WHERE sa.employee.id = :employeeId
          AND sa.rosterDay.dayDate < :currentDate
        ORDER BY sa.rosterDay.dayDate DESC
    """)
    List<ShiftCode> findRecentShiftCodesBeforeDate(
            Long employeeId,
            LocalDate currentDate,
            Pageable pageable
    );

//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    Optional<ShiftAssignment>
//    findWithLockByEmployee_IdAndRosterDay_Id(
//            Long employeeId,
//            Long rosterDayId
//    );

    Optional<ShiftAssignment>
    findByEmployee_IdAndRosterDay_Id(
            Long employeeId,
            Long rosterDayId
    );

    boolean existsByEmployee_IdAndRosterDay_Id(
            Long empId,
            Long dayId
    );

    boolean existsByRosterDay_Id(Long rosterDayId);

    List<ShiftAssignment> findByRosterDay_DayDateBetween(
            LocalDate start,
            LocalDate end
    );
    @Query("""
    SELECT COUNT(sa)
    FROM ShiftAssignment sa
    WHERE sa.rosterDay.id = :rosterDayId
      AND sa.shiftType.code = :shiftCode
      AND sa.onDuty = false
""")
    long countByRosterDayAndShiftCode(
            Long rosterDayId,
            ShiftCode shiftCode
    );

    @Query("""
    SELECT e
    FROM Employee e
    WHERE e.active = true
      AND e.id NOT IN (
          SELECT sa.employee.id
          FROM ShiftAssignment sa
          WHERE sa.rosterDay.id = :rosterDayId
      )
""")
    List<Employee> findUnassignedEmployees(Long rosterDayId);

    @Query("""
    SELECT sa.employee
    FROM ShiftAssignment sa
    WHERE sa.shiftType.code = :shiftCode
      AND sa.rosterDay.dayDate = :date
""")
    List<Employee> findEmployeesByShiftCodeAndDate(
            ShiftCode shiftCode,
            LocalDate date
    );
    @Query("""
    SELECT sa.employee
    FROM ShiftAssignment sa
    WHERE sa.rosterDay.dayDate = :date
      AND sa.shiftType.code = 'EVENING'
""")
    List<Employee> findEveningAssignedEmployeesByDate(LocalDate date);

    @Query("""
    SELECT COUNT(sa)
    FROM ShiftAssignment sa
    WHERE sa.employee.id = :employeeId
      AND sa.rosterDay.rosterWeek.id = :weekId
      AND sa.shiftType.code = 'ON_DUTY'
""")
    long countOnDutyInWeek(Long employeeId, Long weekId);

    @Query("""
    SELECT COUNT(sa)
    FROM ShiftAssignment sa
    WHERE sa.employee.id = :employeeId
      AND sa.shiftType.code = :shiftCode
      AND sa.rosterDay.dayDate < :currentDate
      AND sa.rosterDay.dayDate >= :currentDateMinusWindow
""")
    long countRecentShiftType(
            Long employeeId,
            ShiftCode shiftCode,
            LocalDate currentDate,
            LocalDate currentDateMinusWindow
    );
    @Query("""
    SELECT COUNT(sa)
    FROM ShiftAssignment sa
    WHERE sa.rosterDay.dayDate = :date
      AND sa.shiftType.code = :shiftCode
      AND sa.onDuty = false
""")
    long countByRosterDay_DayDateAndShiftType_Code(
            LocalDate date,
            ShiftCode shiftCode
    );
    @Query("""
    SELECT DISTINCT sa.rosterDay.rosterWeek.id
    FROM ShiftAssignment sa
    WHERE sa.rosterDay.dayDate = :weekStart
""")
    Long findWeekIdByStartDate(LocalDate weekStart);


    @Modifying
    @Query("""
    DELETE FROM ShiftAssignment sa
    WHERE sa.employee.id = :employeeId
      AND sa.rosterDay.id = :rosterDayId
      AND sa.shiftType.code = :shiftCode
""")
    void deleteByEmployeeAndRosterDayAndShiftType_Code(
            Long employeeId,
            Long rosterDayId,
            ShiftCode shiftCode
    );
}

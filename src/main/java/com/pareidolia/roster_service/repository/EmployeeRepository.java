package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.enumtype.EmployeeLevel;
import com.pareidolia.roster_service.enumtype.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByActiveTrue();

    List<Employee> findByEmployeeLevel(EmployeeLevel employeeLevel);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.active = true
        AND e.id NOT IN (
            SELECT l.employee.id FROM LeaveImport l
            WHERE l.leaveDate = :date
        )
    """)
    List<Employee> findActiveNotOnLeave(LocalDate date);

    List<Employee> findByGenderAndActiveTrue(Gender gender);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    // added
    boolean existsByEmployeeCode(String employeeCode);




}

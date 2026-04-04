package com.pareidolia.roster_service.entity;

import com.pareidolia.roster_service.enumtype.EmployeeLevel;
import com.pareidolia.roster_service.enumtype.Gender;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeCode;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeLevel employeeLevel;
    // JUNIOR, MID_SENIOR, SENIOR

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int maxWeeklyHours;

    public boolean isSenior() {
        return this.employeeLevel == EmployeeLevel.SENIOR;
    }
}

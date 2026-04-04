package com.pareidolia.roster_service.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "leave_import",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"employee_id", "leave_date"})
        }
)
public class LeaveImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate leaveDate;

    private String leaveType;

    private boolean halfDay;
}


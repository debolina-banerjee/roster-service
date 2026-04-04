package com.pareidolia.roster_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shift_assignment",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"employee_id","roster_day_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "roster_day_id", nullable = false)
    private RosterDay rosterDay;

    @ManyToOne
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    private boolean dragged;

    private boolean onDuty;

    // 🔥 NEW – variable hours
    @Column(nullable = false)
    private int actualHours;
}

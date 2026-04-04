package com.pareidolia.roster_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDate;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "weekly_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklySummary {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Long employeeId;

    private LocalDate weekStart;

    private int regularHours;

    private int onDutyHours;

    private int totalHours;

    private int deficit;      // 48 - total

    private int nights;

    private int graveyards;

    private int drags;

    private int leaves;


    private int evenings;

    private String fairnessFlag;
}



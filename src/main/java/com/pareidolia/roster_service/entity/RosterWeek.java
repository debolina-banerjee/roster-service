package com.pareidolia.roster_service.entity;

import com.pareidolia.roster_service.enumtype.RosterStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "roster_week")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate weekStartDate;

    @Column(nullable = false)
    private LocalDate weekEndDate;

    @Column(nullable = false)
    private boolean published;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RosterStatus rosterStatus;
}

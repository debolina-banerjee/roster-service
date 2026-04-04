package com.pareidolia.roster_service.entity;

import com.pareidolia.roster_service.enumtype.DayCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "roster_day",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"roster_week_id", "roster_date"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "roster_week_id", nullable = false)
    private RosterWeek rosterWeek;

    @Column(name="roster_date",nullable = false)
    private LocalDate dayDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayCategory dayCategory;





}

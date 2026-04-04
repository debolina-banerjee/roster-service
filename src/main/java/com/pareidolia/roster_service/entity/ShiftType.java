package com.pareidolia.roster_service.entity;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shift_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ShiftCode code;
    // EARLY_MORNING, EVENING, NIGHT, GRAVEYARD, ON_DUTY

    @Column(nullable = false)
    private int shiftHours;

    @Column(nullable = false)
    private boolean onDuty;


    @Column(nullable = false)
    private boolean active = true;

}

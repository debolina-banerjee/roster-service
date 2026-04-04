package com.pareidolia.roster_service.entity;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.DayCategory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "shift_config",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "roster_week_id",
                                "day_category",
                                "shift_type_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ NEW — week specific config
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roster_week_id", nullable = false)
    private RosterWeek rosterWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_category", nullable = false)
    private DayCategory dayCategory;

    @ManyToOne
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    @Column(nullable = false)
    private int requiredResources;

    @Column(nullable = false)
    private boolean active = true;
}

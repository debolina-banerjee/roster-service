package com.pareidolia.roster_service.dto;

import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftConfigRequestDto {

    @NotNull(message="Day category is required")
    private DayCategory dayCategory;
//    @NotNull(message="Shift type id is required")
//    private Long shiftTypeId;
    @Min(value = 1, message = "Required resources must be at least 1")
    private int requiredResources;
    @NotNull(message = "Active flag is required")
    private Boolean active;
//    @NotNull(message="Roster week id is required")
//    private Long rosterWeekId;
    @NotNull(message="Shift Type code is required")
    private ShiftCode shiftTypeCode;

    @NotNull
    private LocalDate weekStartDate;
}
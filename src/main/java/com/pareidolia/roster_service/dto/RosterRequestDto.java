package com.pareidolia.roster_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterRequestDto {

    @NotNull(message="WeekStart Date is required")
    private LocalDate weekStartDate;
}

package com.pareidolia.roster_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRosterWeekDto {

    @NotNull
    private LocalDate weekStartDate;

}
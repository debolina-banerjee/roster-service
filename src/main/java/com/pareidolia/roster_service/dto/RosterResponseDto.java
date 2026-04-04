package com.pareidolia.roster_service.dto;

import com.pareidolia.roster_service.enumtype.ShiftCode;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterResponseDto {

    private LocalDate date;
    private ShiftCode shiftCode;
    private Long employeeId;
    private String employeeName;
    private boolean onDuty;
    private boolean dragged;
}

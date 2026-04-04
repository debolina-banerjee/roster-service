package com.pareidolia.roster_service.dto;

import com.pareidolia.roster_service.enumtype.EmployeeLevel;
import com.pareidolia.roster_service.enumtype.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeUpdateDto {

    @NotBlank(message = "Full name is mandatory")
    @Size(max = 100)
    private String fullName;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Employee level is required")
    private EmployeeLevel employeeLevel;

    @NotNull(message = "Active flag is required")
    private Boolean active;

    @Min(0)
    @Max(168)
    private int maxWeeklyHours;
}
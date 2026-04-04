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
public class EmployeeDto {

    private Long id;

    @NotBlank(message = "Employee code is mandatory")
    @Size(max = 20, message = "Employee code must be at most 20 characters")
    private String employeeCode;

    @NotBlank(message = "Full name is mandatory")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Employee level is required")
    private EmployeeLevel employeeLevel;

    @NotNull(message = "Active flag is required")
    private Boolean active;   // ❗ change boolean → Boolean

    @Min(value = 0, message = "Max weekly hours cannot be negative")
    @Max(value = 168, message = "Max weekly hours cannot exceed 168")
    private int maxWeeklyHours;
}
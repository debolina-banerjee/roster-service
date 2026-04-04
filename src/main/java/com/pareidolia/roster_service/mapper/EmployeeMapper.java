package com.pareidolia.roster_service.mapper;

import com.pareidolia.roster_service.dto.EmployeeDto;
import com.pareidolia.roster_service.dto.EmployeeUpdateDto;
import com.pareidolia.roster_service.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeDto toDto(Employee employee) {
        if (employee == null) return null;

        return EmployeeDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .gender(employee.getGender())
                .employeeLevel(employee.getEmployeeLevel())
                .active(employee.isActive())
                .maxWeeklyHours(employee.getMaxWeeklyHours())
                .build();
    }

    public Employee toEntity(EmployeeDto dto) {
        if (dto == null) return null;

        return Employee.builder()
                .id(dto.getId())
                .employeeCode(dto.getEmployeeCode())
                .fullName(dto.getFullName())
                .gender(dto.getGender())
                .employeeLevel(dto.getEmployeeLevel())
                .active(Boolean.TRUE.equals(dto.getActive()))
                .maxWeeklyHours(dto.getMaxWeeklyHours())
                .build();
    }

    public void updateEntity(EmployeeUpdateDto dto, Employee employee) {

        employee.setFullName(dto.getFullName());
        employee.setGender(dto.getGender());
        employee.setEmployeeLevel(dto.getEmployeeLevel());
        employee.setActive(dto.getActive());
        employee.setMaxWeeklyHours(dto.getMaxWeeklyHours());
    }

}

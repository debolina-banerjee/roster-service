package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.dto.EmployeeDto;
import com.pareidolia.roster_service.dto.EmployeeUpdateDto;
import com.pareidolia.roster_service.entity.Employee;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.exception.ErrorMessages;
import com.pareidolia.roster_service.mapper.EmployeeMapper;
import com.pareidolia.roster_service.repository.EmployeeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;


    @GetMapping
    public List<EmployeeDto> getAllEmployees() {

        return employeeRepository.findAll()
                .stream()
                .map(employeeMapper::toDto)
                .toList();
    }


    @GetMapping("/{id}")
    public EmployeeDto getEmployeeById(
            @PathVariable @Positive Long id
    ) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_ID + id
                        ));

        return employeeMapper.toDto(employee);
    }


    @GetMapping("/code/{code}")
    public EmployeeDto getEmployeeByCode(@PathVariable String code) {

        Employee employee = employeeRepository.findByEmployeeCode(code)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_CODE + code
                        ));

        return employeeMapper.toDto(employee);
    }


    @PostMapping
    public EmployeeDto createEmployee(@Valid @RequestBody EmployeeDto dto) {

        if (employeeRepository.existsByEmployeeCode(dto.getEmployeeCode())) {
            throw new BusinessRuleException(
                    ErrorMessages.EMPLOYEE_CODE_ALREADY_EXISTS + dto.getEmployeeCode()
            );
        }

        Employee saved = employeeRepository.save(
                employeeMapper.toEntity(dto)
        );

        return employeeMapper.toDto(saved);
    }


    @PutMapping("/{id}")
    public EmployeeDto updateEmployeeById(
            @PathVariable @Positive Long id,
            @Valid @RequestBody EmployeeUpdateDto dto
    ) {
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_ID + id
                        ));

        existing.setFullName(dto.getFullName());
        existing.setGender(dto.getGender());
        existing.setEmployeeLevel(dto.getEmployeeLevel());
        existing.setActive(dto.getActive());
        existing.setMaxWeeklyHours(dto.getMaxWeeklyHours());

        Employee saved = employeeRepository.save(existing);

        return employeeMapper.toDto(saved);
    }


    @PutMapping("/code/{code}")
    public EmployeeDto updateEmployeeByCode(
            @PathVariable String code,
            @Valid @RequestBody EmployeeUpdateDto dto
    ) {
        Employee existing = employeeRepository.findByEmployeeCode(code)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_CODE + code
                        ));


        existing.setFullName(dto.getFullName());
        existing.setGender(dto.getGender());
        existing.setEmployeeLevel(dto.getEmployeeLevel());
        existing.setActive(dto.getActive());
        existing.setMaxWeeklyHours(dto.getMaxWeeklyHours());

        Employee saved = employeeRepository.save(existing);

        return employeeMapper.toDto(saved);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmployeeById(
            @PathVariable @Positive Long id
    ) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_ID + id
                        ));

        employeeRepository.delete(employee);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Employee deleted successfully",
                        "deletedId", id,
                        "timestamp", LocalDateTime.now()
                )
        );
    }


    @DeleteMapping("/code/{code}")
    public ResponseEntity<?> deleteEmployeeByCode(@PathVariable String code) {

        Employee employee = employeeRepository.findByEmployeeCode(code)
                .orElseThrow(() ->
                        new BusinessRuleException(
                                ErrorMessages.EMPLOYEE_NOT_FOUND_CODE + code
                        ));

        employeeRepository.delete(employee);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Employee deleted successfully",
                        "employeeCode", code,
                        "timestamp", LocalDateTime.now()
                )
        );
    }
}

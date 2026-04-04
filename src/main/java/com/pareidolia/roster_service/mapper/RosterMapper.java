package com.pareidolia.roster_service.mapper;

import com.pareidolia.roster_service.dto.RosterResponseDto;
import com.pareidolia.roster_service.entity.ShiftAssignment;
import org.springframework.stereotype.Component;

@Component
public class RosterMapper {

    public RosterResponseDto toDto(ShiftAssignment assignment) {
        if (assignment == null) return null;

        return RosterResponseDto.builder()
                .date(assignment.getRosterDay().getDayDate())
                .shiftCode(assignment.getShiftType().getCode())
                .employeeId(assignment.getEmployee().getId())
                .employeeName(assignment.getEmployee().getFullName())
                .onDuty(assignment.isOnDuty())
                .dragged(assignment.isDragged())
                .build();
    }
}

package com.pareidolia.roster_service.mapper;

import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class ShiftTypeMapper {

    public ShiftTypeDto toDto(ShiftType shiftType) {
        return ShiftTypeDto.builder()
                .id(shiftType.getId())
                .code(shiftType.getCode())
                .shiftHours(shiftType.getShiftHours())
                .onDuty(shiftType.isOnDuty())
                .build();
    }

    @Getter
    @Builder
    public static class ShiftTypeDto {
        private Long id;
        private ShiftCode code;
        private int shiftHours;
        private boolean onDuty;
    }
}

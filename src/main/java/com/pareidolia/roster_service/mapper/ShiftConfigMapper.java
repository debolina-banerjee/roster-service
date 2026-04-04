package com.pareidolia.roster_service.mapper;

import com.pareidolia.roster_service.dto.ShiftConfigRequestDto;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import com.pareidolia.roster_service.exception.BusinessRuleException;
import com.pareidolia.roster_service.repository.RosterWeekRepository;
import com.pareidolia.roster_service.repository.ShiftTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShiftConfigMapper {

    private final ShiftTypeRepository shiftTypeRepository;
    private final RosterWeekRepository rosterWeekRepository;

    public ShiftConfig toEntity(ShiftConfigRequestDto dto) {

        if (dto == null) return null;

        // 1️⃣ Fetch shift type
        ShiftType shiftType =
                shiftTypeRepository.findByCode(dto.getShiftTypeCode())
                        .orElseThrow(() ->
                                new BusinessRuleException(
                                        "Invalid ShiftType Code: " + dto.getShiftTypeCode()
                                )
                        );

        // 2️⃣ Prevent ON_DUTY modification
        if (shiftType.getCode() == ShiftCode.ON_DUTY) {
            throw new BusinessRuleException(
                    "ON_DUTY requirement cannot be modified"
            );
        }

        // 3️⃣ Fetch roster week using weekStartDate
        RosterWeek rosterWeek =
                rosterWeekRepository.findByWeekStartDate(dto.getWeekStartDate())
                        .orElseThrow(() ->
                                new BusinessRuleException(
                                        "Roster week not found for date: " + dto.getWeekStartDate()
                                )
                        );

        // 4️⃣ Build entity
        return ShiftConfig.builder()
                .rosterWeek(rosterWeek)
                .dayCategory(dto.getDayCategory())
                .shiftType(shiftType)
                .requiredResources(dto.getRequiredResources())
                .active(Boolean.TRUE.equals(dto.getActive()))
                .build();
    }
}
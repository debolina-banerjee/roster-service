package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.dto.ShiftConfigRequestDto;
import com.pareidolia.roster_service.entity.RosterWeek;
import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.mapper.ShiftConfigMapper;
import com.pareidolia.roster_service.repository.RosterWeekRepository;
import com.pareidolia.roster_service.repository.ShiftConfigRepository;
import com.pareidolia.roster_service.repository.ShiftTypeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/shift-config")
@RequiredArgsConstructor
public class AdminShiftConfigController {

    private final ShiftConfigRepository shiftConfigRepository;
    private final ShiftConfigMapper shiftConfigMapper;
    private final RosterWeekRepository rosterWeekRepository;
    private final ShiftTypeRepository shiftTypeRepository;

    @GetMapping
    public List<ShiftConfig> getAllConfigs() {
        return shiftConfigRepository.findAll();
    }

    @PostMapping
    public ShiftConfig createConfig(
            @Valid @RequestBody ShiftConfigRequestDto dto
    ) {
        ShiftConfig config = shiftConfigMapper.toEntity(dto);

        return shiftConfigRepository.save(config);
    }

    @PutMapping("/{id}")
    public ShiftConfig updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody ShiftConfigRequestDto dto
    ) {
        ShiftConfig existing =
                shiftConfigRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("ShiftConfig not found"));

        ShiftConfig mapped = shiftConfigMapper.toEntity(dto);

        // ✅ update fields safely
        //existing.setRosterWeek(mapped.getRosterWeek());
        existing.setDayCategory(mapped.getDayCategory());
        existing.setShiftType(mapped.getShiftType());
        existing.setRequiredResources(mapped.getRequiredResources());
        existing.setActive(mapped.isActive());

        return shiftConfigRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public void deleteConfig(@PathVariable Long id) {
        shiftConfigRepository.deleteById(id);
    }


    @GetMapping("/week/{weekId}")
    public List<ShiftConfig> getConfigsByWeek(@PathVariable Long weekId) {
        return shiftConfigRepository.findByRosterWeek_Id(weekId);
    }

    @GetMapping("/week-start/{weekStart}")
    public List<ShiftConfig> getConfigsByWeekStart(
            @PathVariable LocalDate weekStart) {

        RosterWeek week =
                rosterWeekRepository.findByWeekStartDate(weekStart)
                        .orElseThrow(() -> new RuntimeException("Week not found"));

        return shiftConfigRepository.findByRosterWeek_Id(week.getId());
    }

    @PutMapping("/week-start/{weekStart}")
    public ShiftConfig updateRequirement(
            @PathVariable LocalDate weekStart,
            @Valid @RequestBody ShiftConfigRequestDto dto) {

        RosterWeek week =
                rosterWeekRepository.findByWeekStartDate(weekStart)
                        .orElseThrow(() -> new RuntimeException("Week not found"));

        ShiftType shiftType =
                shiftTypeRepository.findByCode(dto.getShiftTypeCode())
                        .orElseThrow(() -> new RuntimeException("ShiftType not found"));

        ShiftConfig config =
                shiftConfigRepository
                        .findByRosterWeek_IdAndDayCategoryAndShiftType_Code(
                                week.getId(),
                                dto.getDayCategory(),
                                shiftType.getCode()
                        )
                        .orElseThrow(() -> new RuntimeException("ShiftConfig not found"));

        config.setRequiredResources(dto.getRequiredResources());
        config.setActive(dto.getActive());

        return shiftConfigRepository.save(config);
    }
}


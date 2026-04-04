package com.pareidolia.roster_service.repository;

import com.pareidolia.roster_service.entity.ShiftConfig;
import com.pareidolia.roster_service.entity.ShiftType;
import com.pareidolia.roster_service.enumtype.DayCategory;
import com.pareidolia.roster_service.enumtype.ShiftCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    Optional<ShiftType> findByCode(ShiftCode code);

}


package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class FairDistributionService {
    public List<Employee> sortByLoad(
            List<Employee> employees,
            Map<Long, Integer> weeklyCount
    ) {
        return employees.stream()
                .sorted(Comparator.comparingInt(
                        e -> weeklyCount.getOrDefault(e.getId(), 0)
                ))
                .toList();
    }
}

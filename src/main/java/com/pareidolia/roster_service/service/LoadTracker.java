package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LoadTracker {
    public Map<Long, Integer> init(List<Employee> employees) {

        Map<Long, Integer> map = new HashMap<>();

        for (Employee e : employees) {
            map.put(e.getId(), 0);
        }

        return map;
    }
}

package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.Employee;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ReviewerUtil {

    private static final Set<String> REVIEWERS = Set.of(
            "Vaskar Mondal",
            "Supratik Das",
            "Rounak Datta",
            "Sridhar Bhowmick",
            "Rohan Samanta",
            "Biplab Baguli",
            "Ankita Mahapatro",
            "Malay Dey",
            "Supriyo Roy",
            "Rai Sarkar"
    );

    public boolean isReviewer(Employee employee) {
        return employee != null
                && employee.getFullName() != null
                && REVIEWERS.contains(employee.getFullName().trim());
    }
}
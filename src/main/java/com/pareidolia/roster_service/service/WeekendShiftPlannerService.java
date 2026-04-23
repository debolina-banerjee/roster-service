package com.pareidolia.roster_service.service;

import com.pareidolia.roster_service.entity.RosterDay;

public interface WeekendShiftPlannerService {
    void planDay(RosterDay rosterDay);
}
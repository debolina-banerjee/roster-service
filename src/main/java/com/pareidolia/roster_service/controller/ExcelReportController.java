package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.service.ExcelReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ExcelReportController {

    private final ExcelReportService service;

    @GetMapping("/roster-excel/{weekStart}")
    public ResponseEntity<byte[]> excel(
            @PathVariable LocalDate weekStart
    ) throws Exception {

        byte[] data = service.weeklyMatrix(weekStart);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Roster_"+weekStart+".xlsx"
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(data);
    }
}

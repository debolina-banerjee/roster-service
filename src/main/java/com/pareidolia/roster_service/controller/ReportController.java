package com.pareidolia.roster_service.controller;

import com.pareidolia.roster_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/csv")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @GetMapping("/roster/{weekStart}")
    public ResponseEntity<byte[]> roster(
            @PathVariable LocalDate weekStart) {

        byte[] data = service.generateRosterCsv(weekStart);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=roster_" + weekStart + ".csv")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @GetMapping("/summary/{weekStart}")
    public ResponseEntity<byte[]> summary(
            @PathVariable LocalDate weekStart) {

        byte[] data = service.generateSummaryCsv(weekStart);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=summary_" + weekStart + ".csv")
                .body(data);
    }

    @GetMapping("/audit/{weekStart}")
    public ResponseEntity<byte[]> audit(
            @PathVariable LocalDate weekStart) {

        byte[] data = service.generateAuditCsv(weekStart);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_" + weekStart + ".csv")
                .body(data);
    }
}

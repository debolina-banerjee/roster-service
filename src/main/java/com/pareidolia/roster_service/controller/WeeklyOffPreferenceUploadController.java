package com.pareidolia.roster_service.controller;


import com.pareidolia.roster_service.service.WeeklyOffPreferenceUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/weekly-off-preference")
@RequiredArgsConstructor
public class WeeklyOffPreferenceUploadController {
    private final WeeklyOffPreferenceUploadService uploadService;

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("weekStartDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStartDate
    ) {
        uploadService.process(file, weekStartDate);
        return "Upload successful";
    }
}

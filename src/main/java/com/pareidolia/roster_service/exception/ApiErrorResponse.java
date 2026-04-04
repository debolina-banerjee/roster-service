package com.pareidolia.roster_service.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ApiErrorResponse {

    private LocalDateTime timestamp;
    private ErrorCodes errorCode;
    private String message;

    // Only for validation errors
    private Map<String, String> validationErrors;
}

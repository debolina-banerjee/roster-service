package com.pareidolia.roster_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRuleException(
            BusinessRuleException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        ErrorCodes.BUSINESS_RULE_VIOLATION,
                        ex.getMessage()
                ));
    }


    @ExceptionHandler(RosterGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleRosterGenerationException(
            RosterGenerationException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        ErrorCodes.INTERNAL_ERROR,
                        ex.getMessage()
                ));
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(
            DataIntegrityViolationException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(
                        ErrorCodes.DUPLICATE_RESOURCE,
                        ErrorMessages.EMPLOYEE_CODE_ALREADY_EXISTS
                ));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(err ->
                        errors.put(err.getField(), err.getDefaultMessage())
                );

        ApiErrorResponse response = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(ErrorCodes.VALIDATION_FAILED)
                .message(ErrorMessages.VALIDATION_FAILED)
                .validationErrors(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {

        ex.printStackTrace(); // keep for debugging

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        ErrorCodes.INTERNAL_ERROR,
                        ErrorMessages.INTERNAL_ERROR
                ));
    }


    private ApiErrorResponse buildError(ErrorCodes code, String message) {

        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(code)
                .message(message)
                .build();
    }
}

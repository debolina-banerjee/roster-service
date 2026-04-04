package com.pareidolia.roster_service.exception;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static final String EMPLOYEE_NOT_FOUND_ID =
            "Employee not found with id: ";

    public static final String EMPLOYEE_NOT_FOUND_CODE =
            "Employee not found with code: ";

    public static final String EMPLOYEE_CODE_ALREADY_EXISTS =
            "Employee code already exists: ";

    public static final String VALIDATION_FAILED =
            "Validation failed. Please check input fields.";

    public static final String INTERNAL_ERROR =
            "Something went wrong. Please try again later.";
}

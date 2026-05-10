package com.example.todos.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    List<FieldViolation> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message,
                                                 List<FieldViolation> fieldErrors) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, fieldErrors);
    }

    public record FieldViolation(String field, String message) {}
}

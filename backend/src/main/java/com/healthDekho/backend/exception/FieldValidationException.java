package com.healthDekho.backend.exception;

/** A single-field validation failure decided in the service (e.g. a rule that needs the current date). Maps to 400. */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

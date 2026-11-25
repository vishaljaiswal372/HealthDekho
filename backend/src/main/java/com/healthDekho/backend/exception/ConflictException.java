package com.healthDekho.backend.exception;

/** The request clashes with existing data (e.g. an email that is already registered). Maps to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

package com.healthDekho.backend.exception;

/** A request whose combination of inputs is not valid (beyond single-field validation). Maps to 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}

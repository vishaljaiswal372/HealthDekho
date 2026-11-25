package com.healthDekho.backend.exception;

/** A request that is well-formed but violates a business rule (e.g. duplicate registration). Maps to 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}

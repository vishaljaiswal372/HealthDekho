package com.healthDekho.backend.ai;

/** The AI model could not produce a usable answer. Maps to 503; no partial results are saved. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

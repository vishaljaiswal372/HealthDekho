package com.healthDekho.backend.exception;

import com.healthDekho.backend.ai.AiUnavailableException;
import com.healthDekho.backend.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(),
                        fe.isBindingFailure() ? "Invalid value" : fe.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, ApiError.of(HttpStatus.BAD_REQUEST,
                "Please correct the highlighted fields.", req.getRequestURI(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(HttpServletRequest req) {
        // Same message for unknown email and wrong password, so accounts can't be enumerated.
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.", req);
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    ResponseEntity<ApiError> accountUnavailable(HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "This account is not active. Please contact HealthDekho support.", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(AiUnavailableException.class)
    ResponseEntity<ApiError> aiUnavailable(HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "AI recommendations are not available right now. Please try again later, "
                        + "or browse the available products on your dashboard.", req);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ApiError> fieldValidation(FieldValidationException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ApiError.of(HttpStatus.BAD_REQUEST,
                "Please correct the highlighted fields.", req.getRequestURI(), Map.of(ex.getField(), ex.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> invalidRequest(InvalidRequestException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> businessRule(BusinessRuleException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data.", req);
    }

    /** Validation of @RequestParam / @PathVariable constraints such as @Positive. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> parameterValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> fields.putIfAbsent(
                r.getMethodParameter().getParameterName(),
                r.getResolvableErrors().getFirst().getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, ApiError.of(HttpStatus.BAD_REQUEST,
                "Please correct the highlighted fields.", req.getRequestURI(), fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, ApiError.of(HttpStatus.BAD_REQUEST,
                "Please correct the highlighted fields.", req.getRequestURI(),
                Map.of(ex.getName(), "Invalid value")));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ResponseEntity<ApiError> notAuthenticated(HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Please log in to continue.", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        // Spring MVC's own exceptions (missing parameter, 404 route, wrong method...) carry their status.
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            String detail = er.getBody().getDetail();
            return error(status, detail != null ? detail : status.getReasonPhrase(), req);
        }
        log.error("Unhandled error on {}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", req);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req) {
        return respond(status, ApiError.of(status, message, req.getRequestURI()));
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status).body(body);
    }
}

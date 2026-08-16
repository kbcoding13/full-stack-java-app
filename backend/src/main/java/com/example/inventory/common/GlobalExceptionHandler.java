package com.example.inventory.common;

import com.example.inventory.common.ApiExceptions.ConflictException;
import com.example.inventory.common.ApiExceptions.DomainRuleException;
import com.example.inventory.common.ApiExceptions.NotFoundException;
import com.example.inventory.common.ApiExceptions.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Single place that turns exceptions into RFC 7807 responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(DomainRuleException.class)
    public ProblemDetail handleDomainRule(DomainRuleException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", request);

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Backstop for constraints the service layer did not check first — including the
     * product_stock non-negative check, which fires when concurrent movements race.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String root = rootMessage(ex);

        if (root.contains("ck_product_stock_non_negative")) {
            return problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Business rule violated",
                    "This movement would take stock below zero",
                    request);
        }
        if (root.contains("append-only")) {
            return problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Business rule violated",
                    "Stock movements cannot be edited or deleted; record a compensating ADJUST instead",
                    request);
        }

        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), root);
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required", request);
    }

    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorage(StorageException ex, HttpServletRequest request) {
        log.error("Object storage failure on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.BAD_GATEWAY, "Storage unavailable", "File storage is unavailable", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Something went wrong on our side", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    private String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "" : cursor.getMessage();
    }
}

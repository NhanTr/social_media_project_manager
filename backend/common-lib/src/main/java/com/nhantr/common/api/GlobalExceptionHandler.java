package com.nhantr.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        return problem(exception.status().value(), exception.code(), exception.getMessage(), request, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException exception, HttpServletRequest request) {
        return problem(400, "INVALID_REQUEST", exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(400, "VALIDATION_FAILED", "Request validation failed.", request, errors);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return problem(405, "METHOD_NOT_ALLOWED", exception.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error(
                "Unhandled exception for {} {} (requestId={})",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("X-Request-Id"),
                exception
        );
        return problem(500, "INTERNAL_ERROR", "An unexpected error occurred.", request, null);
    }

    private ResponseEntity<ProblemDetail> problem(
            int status,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("https://loccoc.app/problems/" + code.toLowerCase()));
        problem.setTitle(code);
        problem.setProperty("code", code);
        problem.setProperty("requestId", request.getHeader("X-Request-Id"));
        if (fieldErrors != null) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(problem);
    }
}

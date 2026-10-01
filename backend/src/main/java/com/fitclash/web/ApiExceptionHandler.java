// File: src/main/java/com/fitclash/web/ApiExceptionHandler.java
package com.fitclash.web;

import com.fitclash.service.AntiCheatException;
import com.fitclash.service.ApiException;
import com.fitclash.web.dto.Dtos.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AntiCheatException.class)
    public ResponseEntity<ApiError> onAntiCheat(AntiCheatException ex) {
        // 422: the request was well-formed, the numbers were not believable.
        return ResponseEntity.unprocessableEntity().body(new ApiError(
                "integrity_check_failed",
                "That entry did not pass the integrity check, so nothing was logged.",
                ex.getViolations()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> onApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", "Check the highlighted fields.", details));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> onConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "concurrent_update",
                "Another device updated your character a moment ago. Send that set again.",
                List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> onDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Database rejected a write: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "constraint_violation",
                "That would break a rule the database enforces.",
                List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                "internal_error", "Something broke on our side.", List.of()));
    }
}

package sg.edu.nus.iss.wellness.error;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Converts backend exceptions into stable JSON error responses.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (exception.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }
        return builder.body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}


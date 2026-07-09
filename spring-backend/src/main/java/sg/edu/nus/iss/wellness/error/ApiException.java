package sg.edu.nus.iss.wellness.error;

import org.springframework.http.HttpStatus;

/**
 * API exception carrying an HTTP status code and, optionally, a
 * {@code Retry-After} hint (in seconds) used by throttled {@code 429} responses.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Factory for a throttled response that advertises when the caller may retry. */
    public static ApiException tooManyRequests(String message, long retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, retryAfterSeconds);
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** @return seconds until the caller may retry, or {@code null} when not applicable. */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

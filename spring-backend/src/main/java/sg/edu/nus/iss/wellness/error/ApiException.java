package sg.edu.nus.iss.wellness.error;

import org.springframework.http.HttpStatus;

/**
 * API exception carrying an HTTP status code.
 *
 * @author SA62 Team
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}


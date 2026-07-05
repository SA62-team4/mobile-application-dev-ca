package sg.edu.nus.iss.wellness.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sg.edu.nus.iss.wellness.config.AppProperties;
import sg.edu.nus.iss.wellness.error.GlobalExceptionHandler;

/**
 * Security entry point that forces the client back to the login page when a request reaches a
 * protected endpoint without a usable session.
 *
 * <p>When the failure is a token problem — expired, missing, or malformed (as classified by
 * {@link JwtAuthenticationFilter}) — this issues an HTTP 302 redirect to the configured login
 * URL so the user can re-enter their credentials. Any other authentication failure (for example
 * a structurally valid token for an account that no longer exists) keeps the original 401 JSON
 * response so genuine API errors are not masked as redirects.</p>
 *
 * @author JustinChua97
 */
@Component
public class LoginRedirectAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final String loginRedirectUrl;
    private final ObjectMapper objectMapper;

    public LoginRedirectAuthenticationEntryPoint(AppProperties properties, ObjectMapper objectMapper) {
        this.loginRedirectUrl = properties.getAuth().getLoginRedirectUrl();
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        AuthFailureReason reason = (AuthFailureReason) request.getAttribute(AuthFailureReason.ATTRIBUTE);
        if (shouldRedirect(reason)) {
            sendLoginRedirect(response);
        } else {
            writeUnauthorized(request, response);
        }
    }

    /**
     * The redirect-URL function: sends an HTTP 302 whose {@code Location} header points at the
     * configured login URL, forcing the client to re-authenticate.
     */
    void sendLoginRedirect(HttpServletResponse response) {
        response.setStatus(HttpStatus.FOUND.value()); // 302
        response.setHeader(HttpHeaders.LOCATION, loginRedirectUrl);
    }

    /**
     * A token-level failure (or an unclassified one) should bounce the user to login; only a
     * concrete {@link AuthFailureReason#USER_INVALID} keeps the 401 response.
     */
    static boolean shouldRedirect(AuthFailureReason reason) {
        return reason != AuthFailureReason.USER_INVALID;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = new GlobalExceptionHandler.ErrorResponse(
                Instant.now(), 401, "Unauthorized", "Authentication required", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Exposed for tests: the URL this entry point redirects to. */
    String getLoginRedirectUrl() {
        return loginRedirectUrl;
    }
}

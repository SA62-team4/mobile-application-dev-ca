package sg.edu.nus.iss.wellness.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.ObjectMapper;

import sg.edu.nus.iss.wellness.config.AppProperties;

/**
 * Verifies that the security layer redirects expired / missing / malformed token requests to the
 * configured login URL, and only falls back to 401 for a genuinely unknown account.
 *
 * @author JustinChua97
 */
class LoginRedirectAuthenticationEntryPointTest {
    private static final String LOGIN_URL = "/api/auth/login";

    private LoginRedirectAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setLoginRedirectUrl(LOGIN_URL);
        entryPoint = new LoginRedirectAuthenticationEntryPoint(properties, new ObjectMapper());
    }

    @Test
    void expiredToken_redirectsToLoginUrl() throws Exception {
        MockHttpServletResponse response = commenceWith(AuthFailureReason.EXPIRED);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(LOGIN_URL);
    }

    @Test
    void missingToken_redirectsToLoginUrl() throws Exception {
        MockHttpServletResponse response = commenceWith(AuthFailureReason.MISSING);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(LOGIN_URL);
    }

    @Test
    void malformedToken_redirectsToLoginUrl() throws Exception {
        MockHttpServletResponse response = commenceWith(AuthFailureReason.MALFORMED);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(LOGIN_URL);
    }

    @Test
    void unclassifiedFailure_redirectsToLoginUrl() throws Exception {
        // No reason attribute set at all still forces the user back to login.
        MockHttpServletResponse response = commenceWith(null);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(LOGIN_URL);
    }

    @Test
    void unknownAccount_returnsUnauthorizedWithoutRedirect() throws Exception {
        MockHttpServletResponse response = commenceWith(AuthFailureReason.USER_INVALID);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void redirectHonoursConfiguredUrl() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getAuth().setLoginRedirectUrl("https://app.example.com/login");
        var customEntryPoint = new LoginRedirectAuthenticationEntryPoint(properties, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthFailureReason.ATTRIBUTE, AuthFailureReason.EXPIRED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        customEntryPoint.commence(request, response, new BadCredentialsException("expired"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("https://app.example.com/login");
    }

    private MockHttpServletResponse commenceWith(AuthFailureReason reason) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wellness-records");
        if (reason != null) {
            request.setAttribute(AuthFailureReason.ATTRIBUTE, reason);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new BadCredentialsException("auth failed"));
        return response;
    }
}

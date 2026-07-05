package sg.edu.nus.iss.wellness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration values loaded from environment variables.
 *
 * @author SA62 Team
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Auth auth = new Auth();
    private String aiServiceUrl;
    private String internalServiceToken;
    private Google google = new Google();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public String getAiServiceUrl() {
        return aiServiceUrl;
    }

    public void setAiServiceUrl(String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
    }

    public String getInternalServiceToken() {
        return internalServiceToken;
    }

    public void setInternalServiceToken(String internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }

    public Google getGoogle() {
        return google;
    }

    public void setGoogle(Google google) {
        this.google = google;
    }

    public static class Jwt {
        private String secret;
        private long expirySeconds;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirySeconds() {
            return expirySeconds;
        }

        public void setExpirySeconds(long expirySeconds) {
            this.expirySeconds = expirySeconds;
        }
    }

    /**
     * Authentication-flow settings.
     *
     * @author JustinChua97
     */
    public static class Auth {
        /**
         * URL the security layer redirects to (HTTP 302) when a request carries an expired,
         * missing, or malformed JWT. Bound from {@code app.auth.login-redirect-url}, which the
         * bundled application.yml maps to the {@code LOGIN_REDIRECT_URL} environment variable.
         * Defaults to the login endpoint so the app works without extra configuration.
         */
        private String loginRedirectUrl = "/api/auth/login";

        public String getLoginRedirectUrl() {
            return loginRedirectUrl;
        }

        public void setLoginRedirectUrl(String loginRedirectUrl) {
            this.loginRedirectUrl = loginRedirectUrl;
        }
    }

    public static class Google {
        private String clientId;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }
}


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
    private String aiServiceUrl;
    private String internalServiceToken;

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
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
}


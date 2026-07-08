package sg.edu.nus.iss.wellness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration values loaded from environment variables.
 *
 * @author Chua Wei Yi Justin, Kumaraguru Surya, Tiong Zhong Cheng, Tang Chee Seng
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private String aiServiceUrl;
    private String internalServiceToken;
    private Google google = new Google();
    private String premiumAiUrl;
    private String premiumAiSecret;

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

    public static class Google {
        private String clientId;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public String getPremiumAiUrl (){
        return premiumAiUrl;
    }

    public void setPremiumAiUrl(String premiumAiUrl) {
        this.premiumAiUrl = premiumAiUrl;
    }

    public String getPremiumAiSecret() {
        return premiumAiSecret;
    }

    public void setPremiumAiSecret(String premiumAiSecret) {
        this.premiumAiSecret = premiumAiSecret;
    }
}


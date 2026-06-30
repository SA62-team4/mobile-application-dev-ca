package sg.edu.nus.iss.wellness.service;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.wellness.config.AppProperties;

import java.util.List;

/**
 * Validates Google ID tokens received from the Android client for SSO login.
 *
 * @author Surya Kumaraguru
 */
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties properties) {
        String clientId = properties.getGoogle().getClientId();
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(clientId)
        );
        nimbusDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienceValidator)
        );
        this.decoder = nimbusDecoder;
    }

    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid Google ID token: " + e.getMessage(), e);
        }

        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
        if (!"accounts.google.com".equals(issuer) && !"https://accounts.google.com".equals(issuer)) {
            throw new IllegalArgumentException("Invalid token issuer");
        }

        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String sub = jwt.getSubject();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google token missing email claim");
        }

        return new GoogleUserInfo(sub, email, name);
    }

    public record GoogleUserInfo(String sub, String email, String name) {
    }
}

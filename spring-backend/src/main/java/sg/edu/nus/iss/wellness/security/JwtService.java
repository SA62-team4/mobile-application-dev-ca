package sg.edu.nus.iss.wellness.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.wellness.config.AppProperties;
import sg.edu.nus.iss.wellness.model.AppUser;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Creates and validates JWT access tokens.
 *
 * @author SA62 Team
 */
@Service
public class JwtService {
    private final AppProperties properties;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of("uid", user.getId(), "name", user.getDisplayName()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwt().getExpirySeconds())))
                .signWith(secretKey())
                .compact();
    }

    public String extractSubject(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, String expectedSubject) {
        Claims claims = claims(token);
        return expectedSubject.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    public long expirySeconds() {
        return properties.getJwt().getExpirySeconds();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey secretKey() {
        String secret = properties.getJwt().getSecret();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ignored) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}


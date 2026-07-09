package sg.edu.nus.iss.wellness.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory login throttling / lockout (S-04 security hardening).
 *
 * <p>Tracks consecutive failed credential attempts per account email and locks
 * the account for a fixed cooling-off window once the failure count exceeds the
 * configured threshold ("more than N" &rarr; the {@code (N + 1)}-th failure
 * locks). While locked, callers short-circuit with {@code 429} before any
 * credential check, so a correct password during the window is still refused.
 *
 * <p>State is per-instance and non-persistent: it resets on restart and is not
 * shared with the .NET backup backend — each backend throttles independently,
 * which is sufficient because they front the same clients. Callers are
 * responsible for the policy around <em>what</em> to record:
 * <ul>
 *   <li>Login records a failure only for an existing, <em>active</em> account,
 *       so deactivated and deleted/unknown accounts never accrue a lockout.</li>
 *   <li>Reactivation records a failure for any existing account (its password
 *       gate is a brute-force surface even though the account is inactive).</li>
 * </ul>
 *
 * @author Chua Wei Yi Justin
 */
@Service
public class LoginAttemptService {
    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.lockout-seconds:180}") long lockoutSeconds) {
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofSeconds(lockoutSeconds);
    }

    /**
     * @return seconds remaining on an active lockout for this email, or {@code 0}
     *         if the account is not currently locked. Elapsed locks are pruned.
     */
    public long secondsUntilUnlock(String email) {
        String key = key(email);
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil == null) {
            return 0;
        }
        Instant now = Instant.now();
        if (!attempt.lockedUntil.isAfter(now)) {
            attempts.remove(key);   // window elapsed; clean slate
            return 0;
        }
        // Still locked: report a positive, ceiling-rounded remaining time.
        long millis = Duration.between(now, attempt.lockedUntil).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    /**
     * Records one failed credential attempt for this email, locking the account
     * once the running failure count exceeds the configured threshold.
     */
    public void recordFailure(String email) {
        attempts.compute(key(email), (k, existing) -> {
            Instant now = Instant.now();
            Attempt attempt = (existing == null || existing.expired(now)) ? new Attempt() : existing;
            attempt.failures++;
            if (attempt.failures > maxAttempts) {
                attempt.lockedUntil = now.plus(lockoutDuration);
            }
            return attempt;
        });
    }

    /** Clears all recorded state for this email after a successful sign-in. */
    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    /** Drops all tracked accounts (e.g. an administrative unlock, or test reset). */
    public void clear() {
        attempts.clear();
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static final class Attempt {
        private int failures;
        private Instant lockedUntil;

        private boolean expired(Instant now) {
            return lockedUntil != null && now.isAfter(lockedUntil);
        }
    }
}

using System.Collections.Concurrent;

namespace Wellness.Backup.Api.Services;

/// <summary>
/// In-memory login throttling / lockout (S-04 security hardening), mirroring the
/// Spring backend's behaviour.
/// </summary>
/// <remarks>
/// Tracks consecutive failed credential attempts per account email and locks the
/// account for a fixed cooling-off window once the failure count exceeds the
/// configured threshold ("more than N" &#8594; the (N+1)-th failure locks). While
/// locked, callers short-circuit with <c>429</c> before any credential check, so a
/// correct password during the window is still refused.
/// <para/>
/// State is per-instance and non-persistent (resets on restart) and is not shared
/// with the Spring backend — each backend throttles independently. Registered as a
/// singleton so the map is shared across requests.
/// <para/>@author Chua Wei Yi Justin
/// </remarks>
public sealed class LoginAttemptService
{
    private readonly int _maxAttempts;
    private readonly TimeSpan _lockoutDuration;
    private readonly ConcurrentDictionary<string, Attempt> _attempts = new();

    public LoginAttemptService(int maxAttempts, TimeSpan lockoutDuration)
    {
        _maxAttempts = maxAttempts;
        _lockoutDuration = lockoutDuration;
    }

    /// <summary>
    /// Seconds remaining on an active lockout for this email, or <c>0</c> if the
    /// account is not currently locked. Elapsed locks are pruned on read.
    /// </summary>
    public long SecondsUntilUnlock(string? email)
    {
        var key = Key(email);
        if (!_attempts.TryGetValue(key, out var attempt) || attempt.LockedUntil is null)
        {
            return 0;
        }

        var remaining = (long)Math.Ceiling((attempt.LockedUntil.Value - DateTimeOffset.UtcNow).TotalSeconds);
        if (remaining <= 0)
        {
            _attempts.TryRemove(key, out _);
            return 0;
        }

        return remaining;
    }

    /// <summary>
    /// Records one failed credential attempt, locking the account once the running
    /// failure count exceeds the configured threshold.
    /// </summary>
    public void RecordFailure(string? email)
    {
        _attempts.AddOrUpdate(
            Key(email),
            _ => Lock(new Attempt { Failures = 1 }),
            (_, existing) =>
            {
                var now = DateTimeOffset.UtcNow;
                var attempt = existing.Expired(now) ? new Attempt() : existing;
                attempt.Failures++;
                return Lock(attempt);
            });
    }

    /// <summary>Clears all recorded state for this email after a successful sign-in.</summary>
    public void RecordSuccess(string? email) => _attempts.TryRemove(Key(email), out _);

    /// <summary>Drops all tracked accounts (administrative unlock, or test reset).</summary>
    public void Clear() => _attempts.Clear();

    private Attempt Lock(Attempt attempt)
    {
        if (attempt.Failures > _maxAttempts)
        {
            attempt.LockedUntil = DateTimeOffset.UtcNow.Add(_lockoutDuration);
        }

        return attempt;
    }

    private static string Key(string? email) => email?.Trim().ToLowerInvariant() ?? string.Empty;

    private sealed class Attempt
    {
        public int Failures;
        public DateTimeOffset? LockedUntil;

        public bool Expired(DateTimeOffset now) => LockedUntil is not null && now > LockedUntil.Value;
    }
}

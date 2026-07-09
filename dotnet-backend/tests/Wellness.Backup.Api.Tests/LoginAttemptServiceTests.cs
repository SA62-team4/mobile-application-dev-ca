using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the in-memory login throttle (S-04): the account locks only once the
/// failure count exceeds the threshold, the lock advertises a positive retry
/// window, and a success clears the counter.
/// </summary>
/// <remarks>@author Chua Wei Yi Justin</remarks>
public sealed class LoginAttemptServiceTests
{
    private const string Email = "alice@example.com";

    private static LoginAttemptService NewService(int maxAttempts = 5, int lockoutSeconds = 180) =>
        new(maxAttempts, TimeSpan.FromSeconds(lockoutSeconds));

    [Fact]
    public void FifthFailure_DoesNotLock_ButSixthDoes()
    {
        var service = NewService();

        for (var i = 0; i < 5; i++)
        {
            service.RecordFailure(Email);
        }
        Assert.Equal(0, service.SecondsUntilUnlock(Email));

        service.RecordFailure(Email);
        Assert.True(service.SecondsUntilUnlock(Email) > 0);
    }

    [Fact]
    public void Lock_AdvertisesRetryWindowWithinConfiguredDuration()
    {
        var service = NewService(maxAttempts: 5, lockoutSeconds: 180);

        for (var i = 0; i < 6; i++)
        {
            service.RecordFailure(Email);
        }

        var remaining = service.SecondsUntilUnlock(Email);
        Assert.InRange(remaining, 1, 181);
    }

    [Fact]
    public void RecordSuccess_ResetsCounter()
    {
        var service = NewService();

        for (var i = 0; i < 5; i++)
        {
            service.RecordFailure(Email);
        }
        service.RecordSuccess(Email);

        // One post-reset failure must not lock (would require 6 fresh failures).
        service.RecordFailure(Email);
        Assert.Equal(0, service.SecondsUntilUnlock(Email));
    }

    [Fact]
    public void ExpiredLock_IsClearedOnRead()
    {
        // Zero-second lockout means the window has already elapsed by the read.
        var service = NewService(maxAttempts: 5, lockoutSeconds: 0);

        for (var i = 0; i < 6; i++)
        {
            service.RecordFailure(Email);
        }

        Assert.Equal(0, service.SecondsUntilUnlock(Email));
    }

    [Fact]
    public void Tracking_IsPerEmail()
    {
        var service = NewService();

        for (var i = 0; i < 6; i++)
        {
            service.RecordFailure(Email);
        }

        Assert.True(service.SecondsUntilUnlock(Email) > 0);
        Assert.Equal(0, service.SecondsUntilUnlock("someone-else@example.com"));
    }
}

using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies BCrypt behavior required for Spring Security password compatibility.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class PasswordServiceTests
{
    [Fact]
    public void Verify_AcceptsHashCreatedByService()
    {
        var service = new PasswordService();
        var hash = service.Hash("Password123!");

        Assert.True(service.Verify("Password123!", hash));
        Assert.False(service.Verify("WrongPassword123!", hash));
    }

    [Fact]
    public void Verify_AcceptsHashCreatedBySpringSecurity()
    {
        var service = new PasswordService();
        const string springHash = "$2a$10$iFRjp7.v/5TR/9K4kIxi8O9aL0pwBNvpzRAiC3.Se64xbfsQgn2kW";

        Assert.True(service.Verify("Password123!", springHash));
        Assert.False(service.Verify("WrongPassword123!", springHash));
    }
}

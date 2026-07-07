using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies BCrypt behavior required for Spring Security password compatibility, and that
/// registration stores a salted hash rather than the raw password (E1 Auth &amp; Security).
/// </summary>
/// <remarks>@author Chua Wei Yi Justin, Tiong Zhong Cheng</remarks>
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

    [Fact]
    public void Hash_DoesNotReturnPlaintext_AndUsesBcryptFormat()
    {
        var service = new PasswordService();

        var hash = service.Hash("Password123!");

        Assert.NotEqual("Password123!", hash);
        Assert.StartsWith("$2", hash); // BCrypt hashes begin with $2a/$2b/$2y
    }

    [Fact]
    public void Hash_ProducesDifferentHashesForSamePassword()
    {
        var service = new PasswordService();

        var first = service.Hash("Password123!");
        var second = service.Hash("Password123!");

        Assert.NotEqual(first, second);              // random per-hash salt
        Assert.True(service.Verify("Password123!", first));
        Assert.True(service.Verify("Password123!", second));
    }
}

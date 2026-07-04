using System.IdentityModel.Tokens.Jwt;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies JWT claims required for Spring-compatible backup authentication.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class JwtTokenServiceTests
{
    [Fact]
    public void GenerateToken_UsesExpectedClaimsAndSubject()
    {
        var service = new JwtTokenService(Options());
        var user = new AppUser(42, "demo@example.com", "hash", "Demo User", Role.User, true, DateTime.UtcNow, DateTime.UtcNow);

        var token = service.GenerateToken(user);
        var principal = service.ValidateToken(token);

        Assert.Equal("demo@example.com", principal.FindFirst(JwtRegisteredClaimNames.Sub)?.Value);
        Assert.Equal("42", principal.FindFirst("uid")?.Value);
        Assert.Equal("Demo User", principal.FindFirst("name")?.Value);
        Assert.Equal("USER", principal.FindFirst("role")?.Value);
        Assert.NotNull(principal.FindFirst(JwtRegisteredClaimNames.Iat));
    }

    [Theory]
    [InlineData(Role.User, "USER")]
    [InlineData(Role.PremiumUser, "PREMIUM_USER")]
    public void GenerateToken_WritesEnumRoleAsCanonicalClaim(Role role, string expectedClaim)
    {
        var service = new JwtTokenService(Options());
        var user = new AppUser(1, "demo@example.com", "hash", "Demo User", role, true, DateTime.UtcNow, DateTime.UtcNow);

        var principal = service.ValidateToken(service.GenerateToken(user));

        Assert.Equal(expectedClaim, principal.FindFirst("role")?.Value);
    }

    private static BackendOptions Options()
    {
        return new BackendOptions
        {
            MySqlConnectionString = "Server=localhost;Database=wellness_app;",
            JwtSecret = "unit_test_secret_value_with_at_least_32_chars",
            JwtExpirySeconds = 86400,
            AiServiceUrl = "http://localhost:8000",
            InternalServiceToken = "test-token"
        };
    }
}
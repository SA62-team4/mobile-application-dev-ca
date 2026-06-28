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
        var user = new AppUser(42, "demo@example.com", "hash", "Demo User", "USER", true, DateTime.UtcNow, DateTime.UtcNow);

        var token = service.GenerateToken(user);
        var principal = service.ValidateToken(token);

        Assert.Equal("demo@example.com", principal.FindFirst(JwtRegisteredClaimNames.Sub)?.Value);
        Assert.Equal("42", principal.FindFirst("uid")?.Value);
        Assert.Equal("Demo User", principal.FindFirst("name")?.Value);
        Assert.NotNull(principal.FindFirst(JwtRegisteredClaimNames.Iat));
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

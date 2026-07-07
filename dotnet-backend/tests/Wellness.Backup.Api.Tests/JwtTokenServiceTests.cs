using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies JWT claims and validation required for Spring-compatible backup authentication:
/// a freshly issued token validates and carries the expected claims, and tampered, wrong-secret,
/// or expired tokens are rejected (E1 Auth &amp; Security).
/// </summary>
/// <remarks>@author Chua Wei Yi Justin, Tiong Zhong Cheng</remarks>
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

    [Fact]
    public void ValidateToken_RejectsTamperedToken()
    {
        var service = new JwtTokenService(Options());
        var token = service.GenerateToken(SampleUser());

        // Corrupt the signature segment.
        var tampered = token[..^3] + (token.EndsWith("aaa") ? "bbb" : "aaa");

        Assert.ThrowsAny<SecurityTokenException>(() => service.ValidateToken(tampered));
    }

    [Fact]
    public void ValidateToken_RejectsTokenSignedWithDifferentSecret()
    {
        var foreignToken = new JwtTokenService(Options("a_completely_different_secret_at_least_32_chars"))
            .GenerateToken(SampleUser());
        var validator = new JwtTokenService(Options());

        Assert.ThrowsAny<SecurityTokenException>(() => validator.ValidateToken(foreignToken));
    }

    [Fact]
    public void ValidateToken_RejectsExpiredToken()
    {
        var options = Options();

        Assert.Throws<SecurityTokenExpiredException>(() =>
            new JwtTokenService(options).ValidateToken(ExpiredToken(options)));
    }

    private static AppUser SampleUser() =>
        new(42, "demo@example.com", "hash", "Demo User", Role.User, true, DateTime.UtcNow, DateTime.UtcNow);

    // Builds a correctly signed token whose expiry is already in the past.
    private static string ExpiredToken(BackendOptions options)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(options.JwtSecret));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        var now = DateTime.UtcNow;
        var token = new JwtSecurityToken(
            claims: new[] { new Claim(JwtRegisteredClaimNames.Sub, "demo@example.com") },
            notBefore: now.AddMinutes(-10),
            expires: now.AddMinutes(-5),
            signingCredentials: credentials);
        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    private static BackendOptions Options(string? secret = null)
    {
        return new BackendOptions
        {
            MySqlConnectionString = "Server=localhost;Database=wellness_app;",
            JwtSecret = secret ?? "unit_test_secret_value_with_at_least_32_chars",
            JwtExpirySeconds = 86400,
            AiServiceUrl = "http://localhost:8000",
            InternalServiceToken = "test-token"
        };
    }
}

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Http;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Endpoints;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the protected-endpoint gate rejects a missing, non-Bearer, malformed, or expired
/// token with 401 (E1 Auth &amp; Security). These paths fail before any database access, so the
/// tests need no MySQL — the repository is constructed but never queried.
/// </summary>
/// <remarks>@author Chua Wei Yi Justin</remarks>
public sealed class EndpointAuthorizationTests
{
    [Fact]
    public async Task MissingAuthorizationHeader_ThrowsUnauthorized()
    {
        var exception = await Assert.ThrowsAsync<ApiException>(() => Authorize(NewContext(authorization: null)));
        Assert.Equal(StatusCodes.Status401Unauthorized, exception.StatusCode);
    }

    [Fact]
    public async Task NonBearerHeader_ThrowsUnauthorized()
    {
        var exception = await Assert.ThrowsAsync<ApiException>(() => Authorize(NewContext("Basic dXNlcjpwYXNz")));
        Assert.Equal(StatusCodes.Status401Unauthorized, exception.StatusCode);
    }

    [Fact]
    public async Task MalformedToken_ThrowsUnauthorized()
    {
        var exception = await Assert.ThrowsAsync<ApiException>(() => Authorize(NewContext("Bearer not-a-real-jwt")));
        Assert.Equal(StatusCodes.Status401Unauthorized, exception.StatusCode);
    }

    [Fact]
    public async Task ExpiredToken_ThrowsUnauthorized()
    {
        var options = Options();
        var context = NewContext($"Bearer {ExpiredToken(options)}");

        var exception = await Assert.ThrowsAsync<ApiException>(() => Authorize(context, options));
        Assert.Equal(StatusCodes.Status401Unauthorized, exception.StatusCode);
    }

    private static Task<AppUser> Authorize(HttpContext context, BackendOptions? options = null)
    {
        var resolved = options ?? Options();
        // Constructed but never queried on these paths (no DB connection is opened).
        var users = new UserRepository(new MySqlConnectionFactory(resolved));
        var jwt = new JwtTokenService(resolved);
        return EndpointAuthorization.RequireCurrentUserAsync(context, users, jwt, CancellationToken.None);
    }

    private static DefaultHttpContext NewContext(string? authorization)
    {
        var context = new DefaultHttpContext();
        if (authorization is not null)
        {
            context.Request.Headers.Authorization = authorization;
        }
        return context;
    }

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

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Middleware;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Security;
using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the security layer redirects expired / missing / malformed token requests to the
/// correct login URL, and that a valid token is left alone.
/// </summary>
/// <remarks>@author JustinChua97</remarks>
public sealed class LoginRedirectTests
{
    private const string LoginUrl = "/api/auth/login";

    [Fact]
    public void BuildRedirect_ProducesHttp302ToLoginUrl()
    {
        var result = LoginRedirect.BuildRedirect(LoginUrl);

        var redirect = Assert.IsType<RedirectHttpResult>(result);
        Assert.Equal(LoginUrl, redirect.Url);
        Assert.False(redirect.Permanent); // 302, not 301
        Assert.False(redirect.PreserveMethod);
    }

    [Fact]
    public void ResolveLoginUrl_DefaultsToLoginEndpoint()
    {
        Assert.Equal(LoginUrl, LoginRedirect.ResolveLoginUrl(Options()));
    }

    [Fact]
    public void ResolveLoginUrl_HonoursConfiguredValue()
    {
        Assert.Equal("https://app.example.com/login",
            LoginRedirect.ResolveLoginUrl(Options("https://app.example.com/login")));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("Basic abc123")]
    [InlineData("Bearer ")]
    public void Classify_WithoutBearerToken_IsMissing(string? header)
    {
        Assert.Equal(TokenAuthState.Missing, LoginRedirect.Classify(header, JwtService()));
    }

    [Fact]
    public void Classify_ValidToken_IsValid()
    {
        var options = Options();
        var token = new JwtTokenService(options).GenerateToken(SampleUser());

        Assert.Equal(TokenAuthState.Valid, LoginRedirect.Classify($"Bearer {token}", new JwtTokenService(options)));
    }

    [Fact]
    public void Classify_ExpiredToken_IsExpired()
    {
        var options = Options();

        Assert.Equal(TokenAuthState.Expired,
            LoginRedirect.Classify($"Bearer {ExpiredToken(options)}", new JwtTokenService(options)));
    }

    [Fact]
    public void Classify_GarbageToken_IsMalformed()
    {
        Assert.Equal(TokenAuthState.Malformed,
            LoginRedirect.Classify("Bearer garbage.token.value", JwtService()));
    }

    [Theory]
    [InlineData(TokenAuthState.Missing, true)]
    [InlineData(TokenAuthState.Malformed, true)]
    [InlineData(TokenAuthState.Expired, true)]
    [InlineData(TokenAuthState.Valid, false)]
    public void RequiresLoginRedirect_TriggersOnlyForTokenProblems(TokenAuthState state, bool expected)
    {
        Assert.Equal(expected, LoginRedirect.RequiresLoginRedirect(state));
    }

    [Fact]
    public async Task Middleware_OnLoginRedirectException_Writes302ToConfiguredUrl()
    {
        var services = new ServiceCollection();
        services.AddLogging();

        var context = new DefaultHttpContext { RequestServices = services.BuildServiceProvider() };
        context.Response.Body = new MemoryStream();
        RequestDelegate next = _ => throw new LoginRedirectException();
        var middleware = new ApiErrorMiddleware(next, NullLogger<ApiErrorMiddleware>.Instance, Options());

        await middleware.InvokeAsync(context);

        Assert.Equal(StatusCodes.Status302Found, context.Response.StatusCode);
        Assert.Equal(LoginUrl, context.Response.Headers.Location.ToString());
    }

    private static JwtTokenService JwtService() => new(Options());

    private static AppUser SampleUser() =>
        new(42, "demo@example.com", "hash", "Demo User", Role.User, true, DateTime.UtcNow, DateTime.UtcNow);

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

    private static BackendOptions Options(string? loginRedirectUrl = null)
    {
        return new BackendOptions
        {
            MySqlConnectionString = "Server=localhost;Database=wellness_app;",
            JwtSecret = "unit_test_secret_value_with_at_least_32_chars",
            JwtExpirySeconds = 86400,
            AiServiceUrl = "http://localhost:8000",
            InternalServiceToken = "test-token",
            LoginRedirectUrl = loginRedirectUrl ?? "/api/auth/login"
        };
    }
}

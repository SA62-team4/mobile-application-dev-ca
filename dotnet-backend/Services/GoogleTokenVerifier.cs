using System.IdentityModel.Tokens.Jwt;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;

namespace Wellness.Backup.Api.Services;

/// <summary>
/// Validates Google ID tokens for the optional SSO path in the .NET backup API.
/// </summary>
/// <remarks>@author Chua Wei Yi Justin, Tiong Zhong Cheng</remarks>
public sealed class GoogleTokenVerifier
{
    private const string GoogleJwksUri = "https://www.googleapis.com/oauth2/v3/certs";
    private static readonly string[] ValidIssuers = ["accounts.google.com", "https://accounts.google.com"];

    private readonly HttpClient _httpClient;
    private readonly BackendOptions _options;
    private readonly JwtSecurityTokenHandler _handler = new() { MapInboundClaims = false };

    public GoogleTokenVerifier(HttpClient httpClient, BackendOptions options)
    {
        _httpClient = httpClient;
        _options = options;
    }

    public async Task<GoogleUserInfo> VerifyAsync(string idToken, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(_options.GoogleClientId))
        {
            throw new SecurityTokenException("Google client ID is not configured");
        }

        var jwksJson = await _httpClient.GetStringAsync(GoogleJwksUri, cancellationToken);
        var keys = new JsonWebKeySet(jwksJson).Keys;
        var parameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuers = ValidIssuers,
            ValidateAudience = true,
            ValidAudience = _options.GoogleClientId,
            ValidateIssuerSigningKey = true,
            IssuerSigningKeys = keys,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(2),
            NameClaimType = JwtRegisteredClaimNames.Sub
        };

        var principal = _handler.ValidateToken(idToken, parameters, out _);
        var sub = principal.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;
        var email = principal.FindFirst("email")?.Value;
        var name = principal.FindFirst("name")?.Value;
        if (string.IsNullOrWhiteSpace(sub))
        {
            throw new SecurityTokenException("Google token missing subject claim");
        }
        if (string.IsNullOrWhiteSpace(email))
        {
            throw new SecurityTokenException("Google token missing email claim");
        }

        return new GoogleUserInfo(sub, email, name);
    }

    public sealed record GoogleUserInfo(string Sub, string Email, string? Name);
}

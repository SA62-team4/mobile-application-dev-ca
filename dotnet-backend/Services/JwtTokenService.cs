using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Models;

namespace Wellness.Backup.Api.Services;

/// <summary>
/// Creates and validates Spring-compatible JWT access tokens.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class JwtTokenService
{
    private readonly BackendOptions _options;
    private readonly JwtSecurityTokenHandler _handler = new() { MapInboundClaims = false };

    public JwtTokenService(BackendOptions options)
    {
        _options = options;
    }

    public long ExpirySeconds => _options.JwtExpirySeconds;

    public string GenerateToken(AppUser user)
    {
        var now = DateTimeOffset.UtcNow;
        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Sub, user.Email),
            new("uid", user.Id.ToString(), ClaimValueTypes.Integer64),
            new("name", user.DisplayName),
            new(JwtRegisteredClaimNames.Iat, now.ToUnixTimeSeconds().ToString(), ClaimValueTypes.Integer64)
        };

        var descriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(claims),
            Expires = now.AddSeconds(_options.JwtExpirySeconds).UtcDateTime,
            SigningCredentials = new SigningCredentials(SigningKey(), SecurityAlgorithms.HmacSha256)
        };

        return _handler.WriteToken(_handler.CreateToken(descriptor));
    }

    public ClaimsPrincipal ValidateToken(string token)
    {
        var parameters = new TokenValidationParameters
        {
            ValidateIssuer = false,
            ValidateAudience = false,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = SigningKey(),
            ValidateLifetime = true,
            ClockSkew = TimeSpan.Zero,
            NameClaimType = JwtRegisteredClaimNames.Sub
        };

        return _handler.ValidateToken(token, parameters, out _);
    }

    private SymmetricSecurityKey SigningKey()
    {
        var secret = _options.JwtSecret;
        byte[] keyBytes;
        try
        {
            keyBytes = Convert.FromBase64String(secret);
        }
        catch (FormatException)
        {
            keyBytes = Encoding.UTF8.GetBytes(secret);
        }

        return new SymmetricSecurityKey(keyBytes);
    }
}

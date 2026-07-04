using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Resolves the authenticated user from a bearer token without adding a divergent auth stack.
/// </summary>
/// <remarks>@author SA62 Team, JustinChua97</remarks>
public static class EndpointAuthorization
{
    public static async Task<AppUser> RequireCurrentUserAsync(
        HttpContext context,
        UserRepository users,
        JwtTokenService jwtTokenService,
        CancellationToken cancellationToken,
        Role requiredRole = Role.User)
    {
        var header = context.Request.Headers.Authorization.ToString();
        if (string.IsNullOrWhiteSpace(header) || !header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            throw ApiException.Unauthorized("Authentication required");
        }

        var token = header["Bearer ".Length..].Trim();
        ClaimsPrincipal principal;
        try
        {
            principal = jwtTokenService.ValidateToken(token);
        }
        catch (SecurityTokenException)
        {
            throw ApiException.Unauthorized("Authentication required");
        }
        catch (ArgumentException)
        {
            throw ApiException.Unauthorized("Authentication required");
        }

        var email = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? principal.Identity?.Name;
        if (string.IsNullOrWhiteSpace(email))
        {
            throw ApiException.Unauthorized("Authentication required");
        }

        var user = await users.FindByEmailAsync(email.ToLowerInvariant(), cancellationToken);
        if (user is null || !user.Enabled)
        {
            throw ApiException.Unauthorized("Authenticated user not found");
        }

        // Authorization gate: the database-loaded role (source of truth) must satisfy the
        // required role. Defaults to Role.User, mirroring Spring's hasRole(USER) on every
        // protected route. PREMIUM_USER is intentionally not enforced anywhere yet; when
        // premium features arrive, pass requiredRole: Role.PremiumUser on those endpoints.
        if (!user.HasRole(requiredRole))
        {
            throw ApiException.Forbidden($"Requires the {requiredRole.ToDbValue()} role");
        }

        return user;
    }
}
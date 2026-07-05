using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Services;

namespace Wellness.Backup.Api.Security;

/// <summary>
/// Outcome of inspecting the bearer token on an incoming request.
/// </summary>
/// <remarks>@author JustinChua97</remarks>
public enum TokenAuthState
{
    /// <summary>A valid, unexpired, well-formed token was supplied.</summary>
    Valid,

    /// <summary>No bearer token was supplied.</summary>
    Missing,

    /// <summary>A token was supplied but could not be parsed/verified.</summary>
    Malformed,

    /// <summary>A token was supplied but has expired.</summary>
    Expired
}

/// <summary>
/// Security-layer helper that classifies the caller's JWT and builds the login redirect.
///
/// <para>When the token is expired, missing, or malformed the caller is bounced to the login
/// URL with an HTTP 302 so they can re-enter their credentials. A structurally valid token whose
/// account is unknown is intentionally left to the normal 401 path.</para>
/// </summary>
/// <remarks>@author JustinChua97</remarks>
public static class LoginRedirect
{
    /// <summary>Fallback login URL used when none is configured.</summary>
    public const string DefaultLoginPath = "/api/auth/login";

    /// <summary>Resolves the configured login URL, falling back to <see cref="DefaultLoginPath"/>.</summary>
    public static string ResolveLoginUrl(BackendOptions options) =>
        string.IsNullOrWhiteSpace(options.LoginRedirectUrl) ? DefaultLoginPath : options.LoginRedirectUrl;

    /// <summary>
    /// Inspects the raw Authorization header and reports whether the token is valid, missing,
    /// malformed, or expired.
    /// </summary>
    public static TokenAuthState Classify(string? authorizationHeader, JwtTokenService jwtTokenService)
    {
        if (string.IsNullOrWhiteSpace(authorizationHeader) ||
            !authorizationHeader.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            return TokenAuthState.Missing;
        }

        var token = authorizationHeader["Bearer ".Length..].Trim();
        if (token.Length == 0)
        {
            return TokenAuthState.Missing;
        }

        try
        {
            jwtTokenService.ValidateToken(token);
            return TokenAuthState.Valid;
        }
        catch (SecurityTokenExpiredException)
        {
            return TokenAuthState.Expired;
        }
        catch (SecurityTokenException)
        {
            return TokenAuthState.Malformed;
        }
        catch (ArgumentException)
        {
            return TokenAuthState.Malformed;
        }
    }

    /// <summary>True when the token state should force a redirect to the login page.</summary>
    public static bool RequiresLoginRedirect(TokenAuthState state) =>
        state is TokenAuthState.Missing or TokenAuthState.Malformed or TokenAuthState.Expired;

    /// <summary>
    /// The redirect-URL function: builds an HTTP 302 result whose Location header points at the
    /// login URL. Non-permanent and does not preserve the request method.
    /// </summary>
    public static IResult BuildRedirect(string loginUrl) =>
        Results.Redirect(loginUrl, permanent: false, preserveMethod: false);
}

namespace Wellness.Backup.Api.Errors;

/// <summary>
/// Signals that the current request must be redirected to the login page because its JWT is
/// expired, missing, or malformed. <see cref="Wellness.Backup.Api.Middleware.ApiErrorMiddleware"/>
/// converts this into an HTTP 302 pointing at the configured login URL.
/// </summary>
/// <remarks>@author JustinChua97</remarks>
public sealed class LoginRedirectException : Exception
{
    public LoginRedirectException()
        : base("Authentication required; redirecting to login")
    {
    }
}

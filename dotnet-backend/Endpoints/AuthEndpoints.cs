using Microsoft.IdentityModel.Tokens;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;
using Wellness.Backup.Api.Validation;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Authentication routes mirrored from the Spring Boot backend.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng, Chua Wei Yi Justin</remarks>
public static class AuthEndpoints
{
    public static void MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/auth");

        group.MapPost("/register", async (
            RegisterRequest request,
            UserRepository users,
            PasswordService passwords,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var email = request.Email!.Trim().ToLowerInvariant();
            if (await users.ExistsByEmailAsync(email, cancellationToken))
            {
                throw ApiException.Conflict("Email is already registered");
            }

            var user = await users.CreateAsync(
                request.DisplayName!.Trim(),
                email,
                passwords.Hash(request.Password!),
                cancellationToken);

            return Results.Created($"/api/users/{user.Id}", new UserResponse(user.Id, user.DisplayName, user.Email));
        });

        group.MapPost("/login", async (
            LoginRequest request,
            UserRepository users,
            PasswordService passwords,
            JwtTokenService jwtTokenService,
            LoginAttemptService loginAttempts,
            ILoggerFactory loggerFactory,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var logger = loggerFactory.CreateLogger("Wellness.Backup.Api.Auth");
            var email = request.Email!.Trim().ToLowerInvariant();

            // Locked accounts are refused before any credential check, so a correct
            // password during the cooling-off window is still rejected.
            var retryAfter = loginAttempts.SecondsUntilUnlock(email);
            if (retryAfter > 0)
            {
                throw ApiException.TooManyRequests(
                    $"Too many failed attempts. Try again in {retryAfter} seconds.", retryAfter);
            }

            var user = await users.FindByEmailAsync(email, cancellationToken);
            if (user is null)
            {
                // Unknown/deleted accounts are never recorded (there is nothing to lock).
                logger.LogWarning(".NET backup login failed because user {Email} was not found", email);
                throw ApiException.Unauthorized("Invalid email or password");
            }

            if (!passwords.Verify(request.Password!, user.PasswordHash))
            {
                logger.LogWarning(".NET backup login failed because password verification failed for user {Email}", email);
                // Only active accounts accrue lockout; a deactivated account cannot be
                // logged into anyway, so its failed logins must not trigger a lockout.
                if (user.Enabled)
                {
                    loginAttempts.RecordFailure(email);
                }
                throw ApiException.Unauthorized("Invalid email or password");
            }

            if (!user.Enabled)
            {
                // Distinct 403 so the client can offer reactivation instead of a generic failure.
                throw ApiException.Forbidden("Account is deactivated. Reactivate to continue.");
            }

            loginAttempts.RecordSuccess(email);
            var userResponse = new UserResponse(user.Id, user.DisplayName, user.Email);
            return Results.Ok(new LoginResponse(
                jwtTokenService.GenerateToken(user),
                "Bearer",
                jwtTokenService.ExpirySeconds,
                userResponse));
        });

        group.MapPost("/google", async (
            GoogleAuthRequest request,
            UserRepository users,
            GoogleTokenVerifier googleTokenVerifier,
            JwtTokenService jwtTokenService,
            LoginAttemptService loginAttempts,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            GoogleTokenVerifier.GoogleUserInfo info;
            try
            {
                info = await googleTokenVerifier.VerifyAsync(request.IdToken!, cancellationToken);
            }
            catch (SecurityTokenException e)
            {
                throw ApiException.Unauthorized("Invalid Google token: " + e.Message);
            }
            catch (ArgumentException e)
            {
                throw ApiException.Unauthorized("Invalid Google token: " + e.Message);
            }

            var email = info.Email.Trim().ToLowerInvariant();
            var user = await users.FindByEmailAsync(email, cancellationToken);
            if (user is null)
            {
                var displayName = string.IsNullOrWhiteSpace(info.Name) ? email : info.Name.Trim();
                user = await users.CreateAsync(displayName, email, null, cancellationToken);
            }

            if (!user.Enabled)
            {
                if (!string.IsNullOrWhiteSpace(user.PasswordHash))
                {
                    throw ApiException.Forbidden("Account is disabled");
                }
                if (!request.Reactivate)
                {
                    throw ApiException.Forbidden("Account is deactivated. Confirm reactivation to continue.");
                }

                await users.SetEnabledAsync(user.Id, true, cancellationToken);
                user = user with { Enabled = true };
            }

            // A verified Google identity clears any password-attempt lockout on this
            // account: the lockout guards the password endpoint, not verified SSO.
            loginAttempts.RecordSuccess(email);
            var userResponse = new UserResponse(user.Id, user.DisplayName, user.Email);
            return Results.Ok(new LoginResponse(
                jwtTokenService.GenerateToken(user),
                "Bearer",
                jwtTokenService.ExpirySeconds,
                userResponse));
        });

        // Reactivates a previously deactivated account and logs the user back in.
        // Verifies the same credentials as login, re-enables the account, then
        // returns a token. Idempotent: an already-enabled account simply logs in.
        group.MapPost("/reactivate", async (
            LoginRequest request,
            UserRepository users,
            PasswordService passwords,
            JwtTokenService jwtTokenService,
            LoginAttemptService loginAttempts,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var email = request.Email!.Trim().ToLowerInvariant();

            var retryAfter = loginAttempts.SecondsUntilUnlock(email);
            if (retryAfter > 0)
            {
                throw ApiException.TooManyRequests(
                    $"Too many failed attempts. Try again in {retryAfter} seconds.", retryAfter);
            }

            var user = await users.FindByEmailAsync(email, cancellationToken);
            if (user is null || !passwords.Verify(request.Password!, user.PasswordHash))
            {
                // Reactivation's password gate is a brute-force surface even though the
                // account is deactivated, so it is rate-limited too. Only existing
                // accounts are recorded (a deleted/unknown email is never tracked).
                if (user is not null)
                {
                    loginAttempts.RecordFailure(email);
                }
                throw ApiException.Unauthorized("Invalid email or password");
            }

            if (!user.Enabled)
            {
                await users.SetEnabledAsync(user.Id, true, cancellationToken);
                user = user with { Enabled = true };
            }

            loginAttempts.RecordSuccess(email);
            var userResponse = new UserResponse(user.Id, user.DisplayName, user.Email);
            return Results.Ok(new LoginResponse(
                jwtTokenService.GenerateToken(user),
                "Bearer",
                jwtTokenService.ExpirySeconds,
                userResponse));
        });

        group.MapPost("/logout", () => Results.NoContent());
    }
}

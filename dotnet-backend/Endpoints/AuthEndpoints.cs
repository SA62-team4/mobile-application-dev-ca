using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;
using Wellness.Backup.Api.Validation;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Authentication routes mirrored from the Spring Boot backend.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
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
            ILoggerFactory loggerFactory,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var logger = loggerFactory.CreateLogger("Wellness.Backup.Api.Auth");
            var email = request.Email!.Trim().ToLowerInvariant();
            var user = await users.FindByEmailAsync(email, cancellationToken);
            if (user is null)
            {
                logger.LogWarning(".NET backup login failed because user {Email} was not found", email);
                throw ApiException.Unauthorized("Invalid email or password");
            }

            if (!passwords.Verify(request.Password!, user.PasswordHash))
            {
                logger.LogWarning(".NET backup login failed because password verification failed for user {Email}", email);
                throw ApiException.Unauthorized("Invalid email or password");
            }

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

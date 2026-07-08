using Microsoft.AspNetCore.Mvc;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Models;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Privacy / data-control routes (S-03), mirrored from the Spring Boot backend:
/// export a full JSON copy of the caller's data, reversibly deactivate, or
/// permanently delete (password-confirmed).
/// </summary>
/// <remarks>
/// A wrong password on delete returns 400 — deliberately not 401/403 — because
/// the shared mobile client treats 401/403 as session expiry and would otherwise
/// log the user out on a simple typo.
///
/// @author Chua Wei Yi Justin
/// </remarks>
public static class AccountEndpoints
{
    public static void MapAccountEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/account");

        group.MapGet("/export", async (
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            RecommendationRepository recommendations,
            ChatMessageRepository chatMessages,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);

            var profile = new AccountUserProfile(
                user.Id, user.Email, user.DisplayName, user.Role.ToDbValue(), user.CreatedAt);
            var wellness = await records.ListAsync(user.Id, null, null, cancellationToken);
            var recs = await recommendations.ListAsync(user.Id, cancellationToken);
            var chats = await chatMessages.ListForExportAsync(user.Id, cancellationToken);

            var export = new AccountExport(profile, wellness, recs, chats, DateTime.UtcNow);

            var filename = $"wellness-export-{user.Id}-{DateOnly.FromDateTime(DateTime.UtcNow):yyyy-MM-dd}.json";
            context.Response.Headers.ContentDisposition = $"attachment; filename=\"{filename}\"";
            return Results.Ok(export);
        });

        group.MapPost("/deactivate", async (
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            await users.SetEnabledAsync(user.Id, false, cancellationToken);
            return Results.NoContent();
        });

        // Explicit [FromBody]: minimal APIs do not infer a body for DELETE, and the
        // mobile client sends the password confirmation as a JSON body.
        group.MapDelete("", async (
            [FromBody] DeleteAccountRequest request,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            PasswordService passwords,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);

            if (string.IsNullOrWhiteSpace(request?.Password))
            {
                throw ApiException.BadRequest("Password confirmation is required");
            }
            if (string.IsNullOrWhiteSpace(user.PasswordHash))
            {
                // Google-linked accounts have no local password to confirm against.
                throw ApiException.BadRequest("Password confirmation is unavailable for Google-linked accounts");
            }
            if (!passwords.Verify(request.Password, user.PasswordHash))
            {
                // 400 (not 401/403) so the client does not treat this as session expiry.
                throw ApiException.BadRequest("Incorrect password");
            }

            await users.DeleteAccountAndDataAsync(user.Id, cancellationToken);
            return Results.NoContent();
        });
    }
}

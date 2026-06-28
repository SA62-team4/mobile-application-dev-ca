using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Validation;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Internal routes used only by the Python agentic AI service.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class InternalEndpoints
{
    public static void MapInternalEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/internal/users/{userId:long}");

        group.MapGet("/wellness-records", async (
            long userId,
            int? days,
            HttpContext context,
            BackendOptions options,
            UserRepository users,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            RequireInternalToken(context, options);
            _ = await users.FindByIdAsync(userId, cancellationToken)
                ?? throw ApiException.NotFound("User not found");
            var from = DateOnly.FromDateTime(DateTime.UtcNow).AddDays(-(days ?? 14));
            return Results.Ok(await records.RecentAfterAsync(userId, from, cancellationToken));
        });

        group.MapPost("/recommendations", async (
            long userId,
            InternalRecommendationRequest request,
            HttpContext context,
            BackendOptions options,
            UserRepository users,
            RecommendationRepository recommendations,
            CancellationToken cancellationToken) =>
        {
            RequireInternalToken(context, options);
            RequestValidation.Validate(request);
            _ = await users.FindByIdAsync(userId, cancellationToken)
                ?? throw ApiException.NotFound("User not found");
            var saved = await recommendations.SaveAsync(userId, request, cancellationToken);
            return Results.Created($"/api/recommendations/{saved.Id}", saved);
        });
    }

    private static void RequireInternalToken(HttpContext context, BackendOptions options)
    {
        var token = context.Request.Headers["X-Internal-Service-Token"].ToString();
        if (!string.Equals(token, options.InternalServiceToken, StringComparison.Ordinal))
        {
            throw ApiException.Forbidden("Invalid internal service token");
        }
    }
}

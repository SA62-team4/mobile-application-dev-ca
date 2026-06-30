using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Recommendation routes mirrored from the Spring Boot backend.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class RecommendationEndpoints
{
    public static void MapRecommendationEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/recommendations");

        group.MapPost("/generate", async (
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            AiServiceClient aiServiceClient,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            var recommendation = await aiServiceClient.GenerateRecommendationAsync(user.Id, cancellationToken);
            return Results.Created($"/api/recommendations/{recommendation.Id}", recommendation);
        });

        group.MapGet("", async (
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            RecommendationRepository recommendations,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            return Results.Ok(await recommendations.ListAsync(user.Id, cancellationToken));
        });
    }
}

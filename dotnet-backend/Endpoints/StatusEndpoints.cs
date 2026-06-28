namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Public status endpoints matching the Spring Boot API contract.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class StatusEndpoints
{
    public static void MapStatusEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/", () => Results.Ok(new
        {
            service = "wellness-backend",
            status = "UP",
            health = "/actuator/health"
        }));

        app.MapGet("/actuator/health", () => Results.Ok(new
        {
            status = "UP"
        }));
    }
}

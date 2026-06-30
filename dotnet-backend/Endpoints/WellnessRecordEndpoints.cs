using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;
using Wellness.Backup.Api.Validation;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Wellness CRUD routes mirrored from the Spring Boot backend.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class WellnessRecordEndpoints
{
    public static void MapWellnessRecordEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/wellness-records");

        group.MapPost("", async (
            WellnessRecordRequest request,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            var record = await records.CreateAsync(user.Id, request, cancellationToken);
            return Results.Created($"/api/wellness-records/{record.Id}", record);
        });

        group.MapGet("", async (
            DateOnly? from,
            DateOnly? to,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            return Results.Ok(await records.ListAsync(user.Id, from, to, cancellationToken));
        });

        group.MapGet("/{id:long}", async (
            long id,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            return await records.FindByIdAndUserAsync(id, user.Id, cancellationToken)
                   ?? throw ApiException.NotFound("Wellness record not found");
        });

        group.MapPut("/{id:long}", async (
            long id,
            WellnessRecordRequest request,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            return await records.UpdateAsync(id, user.Id, request, cancellationToken)
                   ?? throw ApiException.NotFound("Wellness record not found");
        });

        group.MapDelete("/{id:long}", async (
            long id,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            if (!await records.DeleteAsync(id, user.Id, cancellationToken))
            {
                throw ApiException.NotFound("Wellness record not found");
            }

            return Results.NoContent();
        });
    }
}

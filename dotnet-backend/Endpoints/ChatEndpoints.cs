using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;
using Wellness.Backup.Api.Validation;

namespace Wellness.Backup.Api.Endpoints;

/// <summary>
/// Chatbot routes that orchestrate through the Python RAG service.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public static class ChatEndpoints
{
    public static void MapChatEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/chat/messages");

        group.MapPost("", async (
            ChatRequest request,
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            WellnessRecordRepository records,
            ChatMessageRepository chatMessages,
            AiServiceClient aiServiceClient,
            CancellationToken cancellationToken) =>
        {
            RequestValidation.Validate(request);
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            var recentRecords = (await records.RecentAfterAsync(user.Id, DateOnly.FromDateTime(DateTime.UtcNow).AddDays(-14), cancellationToken))
                .Select(record => new RecentRecord(
                    record.RecordDate.ToString("yyyy-MM-dd"),
                    decimal.ToDouble(record.SleepHours),
                    record.ExerciseType,
                    record.ExerciseMinutes,
                    record.MoodScore))
                .ToList();

            var aiResponse = await aiServiceClient.ChatAsync(
                new AiChatRequest(user.Id, request.Question!, recentRecords),
                cancellationToken);

            return Results.Ok(await chatMessages.SaveAsync(
                user.Id,
                request.Question!,
                aiResponse.Answer,
                aiResponse.ModelName,
                aiResponse.Sources,
                cancellationToken));
        });

        group.MapGet("", async (
            HttpContext context,
            UserRepository users,
            JwtTokenService jwtTokenService,
            ChatMessageRepository chatMessages,
            CancellationToken cancellationToken) =>
        {
            var user = await EndpointAuthorization.RequireCurrentUserAsync(context, users, jwtTokenService, cancellationToken);
            return Results.Ok(await chatMessages.ListAsync(user.Id, cancellationToken));
        });
    }
}

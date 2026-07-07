using System.Data.Common;
using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Services;

namespace Wellness.Backup.Api.Repositories;

/// <summary>
/// User-scoped persistence for Python-agent recommendations.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class RecommendationRepository
{
    private readonly MySqlConnectionFactory _connections;

    public RecommendationRepository(MySqlConnectionFactory connections)
    {
        _connections = connections;
    }

    public async Task<IReadOnlyList<RecommendationResponse>> ListAsync(long userId, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, title, trend_summary, recommendation_text, action_items, generated_by, created_at
            FROM recommendations
            WHERE user_id = @userId
            ORDER BY created_at DESC
            """,
            connection);
        command.Parameters.AddWithValue("@userId", userId);
        return await ReadRecommendationsAsync(command, cancellationToken);
    }

    public async Task<RecommendationResponse> SaveAsync(long userId, InternalRecommendationRequest request, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            INSERT INTO recommendations
              (user_id, title, trend_summary, recommendation_text, action_items, generated_by, created_at)
            VALUES
              (@userId, @title, @trendSummary, @recommendationText, @actionItems, @generatedBy, UTC_TIMESTAMP(6));
            SELECT LAST_INSERT_ID();
            """,
            connection);
        command.Parameters.AddWithValue("@userId", userId);
        command.Parameters.AddWithValue("@title", request.Title!.Trim());
        command.Parameters.AddWithValue("@trendSummary", request.TrendSummary!.Trim());
        command.Parameters.AddWithValue("@recommendationText", request.RecommendationText!.Trim());
        command.Parameters.AddWithValue("@actionItems", ActionItemText.Join(request.ActionItems));
        command.Parameters.AddWithValue("@generatedBy", string.IsNullOrWhiteSpace(request.GeneratedBy) ? "python-agent" : request.GeneratedBy);
        var id = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return await FindByIdAndUserAsync(id, userId, cancellationToken)
               ?? throw new InvalidOperationException("Created recommendation could not be reloaded");
    }

    private async Task<RecommendationResponse?> FindByIdAndUserAsync(long id, long userId, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, title, trend_summary, recommendation_text, action_items, generated_by, created_at
            FROM recommendations
            WHERE id = @id AND user_id = @userId
            """,
            connection);
        command.Parameters.AddWithValue("@id", id);
        command.Parameters.AddWithValue("@userId", userId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadRecommendation(reader) : null;
    }

    private static async Task<IReadOnlyList<RecommendationResponse>> ReadRecommendationsAsync(MySqlCommand command, CancellationToken cancellationToken)
    {
        var recommendations = new List<RecommendationResponse>();
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            recommendations.Add(ReadRecommendation(reader));
        }

        return recommendations;
    }

    private static RecommendationResponse ReadRecommendation(DbDataReader reader)
    {
        return new RecommendationResponse(
            reader.GetInt64Value("id"),
            reader.GetRequiredString("title"),
            reader.GetRequiredString("trend_summary"),
            reader.GetRequiredString("recommendation_text"),
            ActionItemText.Split(reader.GetOptionalString("action_items")),
            reader.GetRequiredString("generated_by"),
            reader.GetUtcDateTime("created_at"));
    }
}

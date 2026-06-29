using System.Data.Common;
using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Dtos;

namespace Wellness.Backup.Api.Repositories;

/// <summary>
/// User-scoped persistence for stored chatbot exchanges.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class ChatMessageRepository
{
    private readonly MySqlConnectionFactory _connections;

    public ChatMessageRepository(MySqlConnectionFactory connections)
    {
        _connections = connections;
    }

    public async Task<ChatResponse> SaveAsync(
        long userId,
        string question,
        string answer,
        string? modelName,
        IReadOnlyList<SourceSnippet> sources,
        CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            INSERT INTO chat_messages
              (user_id, user_question, assistant_answer, source_summary, model_name, created_at)
            VALUES
              (@userId, @question, @answer, @sourceSummary, @modelName, UTC_TIMESTAMP(6));
            SELECT LAST_INSERT_ID();
            """,
            connection);
        command.Parameters.AddWithValue("@userId", userId);
        command.Parameters.AddWithValue("@question", question);
        command.Parameters.AddWithValue("@answer", answer);
        command.Parameters.AddWithValue("@sourceSummary", SourcesToText(sources));
        command.Parameters.AddWithValue("@modelName", (object?)modelName ?? DBNull.Value);
        var id = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return await FindByIdAndUserAsync(id, userId, sources, cancellationToken)
               ?? throw new InvalidOperationException("Created chat message could not be reloaded");
    }

    public async Task<IReadOnlyList<ChatResponse>> ListAsync(long userId, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, user_question, assistant_answer, model_name, created_at
            FROM chat_messages
            WHERE user_id = @userId
            ORDER BY created_at DESC
            """,
            connection);
        command.Parameters.AddWithValue("@userId", userId);
        var messages = new List<ChatResponse>();
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            messages.Add(ReadMessage(reader, []));
        }

        return messages;
    }

    private async Task<ChatResponse?> FindByIdAndUserAsync(
        long id,
        long userId,
        IReadOnlyList<SourceSnippet> sources,
        CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, user_question, assistant_answer, model_name, created_at
            FROM chat_messages
            WHERE id = @id AND user_id = @userId
            """,
            connection);
        command.Parameters.AddWithValue("@id", id);
        command.Parameters.AddWithValue("@userId", userId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadMessage(reader, sources) : null;
    }

    private static ChatResponse ReadMessage(DbDataReader reader, IReadOnlyList<SourceSnippet> sources)
    {
        return new ChatResponse(
            reader.GetInt64Value("id"),
            reader.GetRequiredString("user_question"),
            reader.GetRequiredString("assistant_answer"),
            sources,
            reader.GetOptionalString("model_name"),
            reader.GetUtcDateTime("created_at"));
    }

    private static string SourcesToText(IReadOnlyList<SourceSnippet> sources)
    {
        return string.Join("\n", sources.Select(source => $"{source.Title}: {source.Snippet}"));
    }
}

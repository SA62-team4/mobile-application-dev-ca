using System.Data.Common;
using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Dtos;

namespace Wellness.Backup.Api.Repositories;

/// <summary>
/// User-scoped persistence for wellness records.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class WellnessRecordRepository
{
    private readonly MySqlConnectionFactory _connections;

    public WellnessRecordRepository(MySqlConnectionFactory connections)
    {
        _connections = connections;
    }

    public async Task<WellnessRecordResponse> CreateAsync(long userId, WellnessRecordRequest request, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            INSERT INTO wellness_records
              (user_id, record_date, sleep_hours, exercise_type, exercise_minutes, mood_score, notes, created_at, updated_at)
            VALUES
              (@userId, @recordDate, @sleepHours, @exerciseType, @exerciseMinutes, @moodScore, @notes, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
            SELECT LAST_INSERT_ID();
            """,
            connection);
        AddRecordParameters(command, userId, request);
        var id = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return await FindByIdAndUserAsync(id, userId, cancellationToken)
               ?? throw new InvalidOperationException("Created wellness record could not be reloaded");
    }

    public async Task<IReadOnlyList<WellnessRecordResponse>> ListAsync(long userId, DateOnly? from, DateOnly? to, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        var hasRange = from is not null && to is not null;
        var sql = hasRange
            ? """
              SELECT id, record_date, sleep_hours, exercise_type, exercise_minutes, mood_score, notes, created_at, updated_at
              FROM wellness_records
              WHERE user_id = @userId AND record_date BETWEEN @from AND @to
              ORDER BY record_date DESC
              """
            : """
              SELECT id, record_date, sleep_hours, exercise_type, exercise_minutes, mood_score, notes, created_at, updated_at
              FROM wellness_records
              WHERE user_id = @userId
              ORDER BY record_date DESC
              """;
        await using var command = new MySqlCommand(sql, connection);
        command.Parameters.AddWithValue("@userId", userId);
        if (hasRange)
        {
            command.Parameters.AddWithValue("@from", from!.Value.ToDateTime(TimeOnly.MinValue));
            command.Parameters.AddWithValue("@to", to!.Value.ToDateTime(TimeOnly.MinValue));
        }

        return await ReadRecordsAsync(command, cancellationToken);
    }

    public async Task<IReadOnlyList<WellnessRecordResponse>> RecentAfterAsync(long userId, DateOnly from, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, record_date, sleep_hours, exercise_type, exercise_minutes, mood_score, notes, created_at, updated_at
            FROM wellness_records
            WHERE user_id = @userId AND record_date > @from
            ORDER BY record_date DESC
            """,
            connection);
        command.Parameters.AddWithValue("@userId", userId);
        command.Parameters.AddWithValue("@from", from.ToDateTime(TimeOnly.MinValue));
        return await ReadRecordsAsync(command, cancellationToken);
    }

    public async Task<WellnessRecordResponse?> FindByIdAndUserAsync(long id, long userId, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            SELECT id, record_date, sleep_hours, exercise_type, exercise_minutes, mood_score, notes, created_at, updated_at
            FROM wellness_records
            WHERE id = @id AND user_id = @userId
            """,
            connection);
        command.Parameters.AddWithValue("@id", id);
        command.Parameters.AddWithValue("@userId", userId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadRecord(reader) : null;
    }

    public async Task<WellnessRecordResponse?> UpdateAsync(long id, long userId, WellnessRecordRequest request, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            UPDATE wellness_records
            SET record_date = @recordDate,
                sleep_hours = @sleepHours,
                exercise_type = @exerciseType,
                exercise_minutes = @exerciseMinutes,
                mood_score = @moodScore,
                notes = @notes,
                updated_at = UTC_TIMESTAMP(6)
            WHERE id = @id AND user_id = @userId
            """,
            connection);
        command.Parameters.AddWithValue("@id", id);
        AddRecordParameters(command, userId, request);
        var affected = await command.ExecuteNonQueryAsync(cancellationToken);
        return affected == 0 ? null : await FindByIdAndUserAsync(id, userId, cancellationToken);
    }

    public async Task<bool> DeleteAsync(long id, long userId, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand("DELETE FROM wellness_records WHERE id = @id AND user_id = @userId", connection);
        command.Parameters.AddWithValue("@id", id);
        command.Parameters.AddWithValue("@userId", userId);
        return await command.ExecuteNonQueryAsync(cancellationToken) > 0;
    }

    private static void AddRecordParameters(MySqlCommand command, long userId, WellnessRecordRequest request)
    {
        command.Parameters.AddWithValue("@userId", userId);
        command.Parameters.AddWithValue("@recordDate", request.RecordDate!.Value.ToDateTime(TimeOnly.MinValue));
        command.Parameters.AddWithValue("@sleepHours", request.SleepHours!.Value);
        command.Parameters.AddWithValue("@exerciseType", (object?)request.ExerciseType ?? DBNull.Value);
        command.Parameters.AddWithValue("@exerciseMinutes", request.ExerciseMinutes!.Value);
        command.Parameters.AddWithValue("@moodScore", request.MoodScore!.Value);
        command.Parameters.AddWithValue("@notes", (object?)request.Notes ?? DBNull.Value);
    }

    private static async Task<IReadOnlyList<WellnessRecordResponse>> ReadRecordsAsync(MySqlCommand command, CancellationToken cancellationToken)
    {
        var records = new List<WellnessRecordResponse>();
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            records.Add(ReadRecord(reader));
        }

        return records;
    }

    private static WellnessRecordResponse ReadRecord(DbDataReader reader)
    {
        return new WellnessRecordResponse(
            reader.GetInt64Value("id"),
            reader.GetDateOnlyValue("record_date"),
            reader.GetDecimalValue("sleep_hours"),
            reader.GetOptionalString("exercise_type"),
            reader.GetInt32Value("exercise_minutes"),
            reader.GetInt32Value("mood_score"),
            reader.GetOptionalString("notes"),
            reader.GetUtcDateTime("created_at"),
            reader.GetUtcDateTime("updated_at"));
    }
}

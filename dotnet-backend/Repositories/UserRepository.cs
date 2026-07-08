using System.Data.Common;
using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Models;

namespace Wellness.Backup.Api.Repositories;

/// <summary>
/// Reads and writes users in the shared `users` table.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng, Chua Wei Yi Justin</remarks>
public sealed class UserRepository
{
    private readonly MySqlConnectionFactory _connections;

    public UserRepository(MySqlConnectionFactory connections)
    {
        _connections = connections;
    }

    public async Task<bool> ExistsByEmailAsync(string email, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand("SELECT COUNT(*) FROM users WHERE email = @email", connection);
        command.Parameters.AddWithValue("@email", email);
        var count = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return count > 0;
    }

    public async Task<AppUser?> FindByEmailAsync(string email, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            "SELECT id, email, password_hash, display_name, role, enabled, created_at, updated_at FROM users WHERE email = @email",
            connection);
        command.Parameters.AddWithValue("@email", email);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadUser(reader) : null;
    }

    public async Task<AppUser?> FindByIdAsync(long id, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            "SELECT id, email, password_hash, display_name, role, enabled, created_at, updated_at FROM users WHERE id = @id",
            connection);
        command.Parameters.AddWithValue("@id", id);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadUser(reader) : null;
    }

    public async Task<AppUser> CreateAsync(string displayName, string email, string passwordHash, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            """
            INSERT INTO users (email, password_hash, display_name, role, enabled, created_at, updated_at)
            VALUES (@email, @passwordHash, @displayName, @role, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
            SELECT LAST_INSERT_ID();
            """,
            connection);
        command.Parameters.AddWithValue("@email", email);
        command.Parameters.AddWithValue("@passwordHash", passwordHash);
        command.Parameters.AddWithValue("@displayName", displayName);
        // New registrations default to the USER role, driven by the enum rather than a literal.
        command.Parameters.AddWithValue("@role", Role.User.ToDbValue());
        var id = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return await FindByIdAsync(id, cancellationToken)
               ?? throw new InvalidOperationException("Created user could not be reloaded");
    }

    /// <summary>
    /// Flips the account's enabled flag (deactivate / reactivate). Returns whether
    /// a row was updated.
    /// </summary>
    public async Task<bool> SetEnabledAsync(long id, bool enabled, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var command = new MySqlCommand(
            "UPDATE users SET enabled = @enabled, updated_at = UTC_TIMESTAMP(6) WHERE id = @id",
            connection);
        command.Parameters.AddWithValue("@enabled", enabled);
        command.Parameters.AddWithValue("@id", id);
        return await command.ExecuteNonQueryAsync(cancellationToken) > 0;
    }

    /// <summary>
    /// Permanently erases the user and all of their owned data in a single
    /// transaction. Children are deleted before the user to satisfy the
    /// user_id foreign keys; if any step fails the whole delete rolls back.
    /// </summary>
    public async Task DeleteAccountAndDataAsync(long id, CancellationToken cancellationToken)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);

        var statements = new[]
        {
            "DELETE FROM chat_messages WHERE user_id = @id",
            "DELETE FROM recommendations WHERE user_id = @id",
            "DELETE FROM wellness_records WHERE user_id = @id",
            "DELETE FROM users WHERE id = @id"
        };

        foreach (var sql in statements)
        {
            await using var command = new MySqlCommand(sql, connection)
            {
                Transaction = (MySqlTransaction)transaction
            };
            command.Parameters.AddWithValue("@id", id);
            await command.ExecuteNonQueryAsync(cancellationToken);
        }

        // Committing on success only; an exception before this point disposes the
        // transaction via `await using`, which rolls the whole batch back.
        await transaction.CommitAsync(cancellationToken);
    }

    private static AppUser ReadUser(DbDataReader reader)
    {
        return new AppUser(
            reader.GetInt64Value("id"),
            reader.GetRequiredString("email"),
            reader.GetRequiredString("password_hash"),
            reader.GetRequiredString("display_name"),
            RoleExtensions.FromDbValue(reader.GetRequiredString("role")),
            reader.GetBooleanValue("enabled"),
            reader.GetUtcDateTime("created_at"),
            reader.GetUtcDateTime("updated_at"));
    }
}

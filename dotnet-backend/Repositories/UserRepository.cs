using System.Data.Common;
using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Models;

namespace Wellness.Backup.Api.Repositories;

/// <summary>
/// Reads and writes users in the shared `users` table.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
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
            VALUES (@email, @passwordHash, @displayName, 'USER', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
            SELECT LAST_INSERT_ID();
            """,
            connection);
        command.Parameters.AddWithValue("@email", email);
        command.Parameters.AddWithValue("@passwordHash", passwordHash);
        command.Parameters.AddWithValue("@displayName", displayName);
        var id = Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
        return await FindByIdAsync(id, cancellationToken)
               ?? throw new InvalidOperationException("Created user could not be reloaded");
    }

    private static AppUser ReadUser(DbDataReader reader)
    {
        return new AppUser(
            reader.GetInt64Value("id"),
            reader.GetRequiredString("email"),
            reader.GetRequiredString("password_hash"),
            reader.GetRequiredString("display_name"),
            reader.GetRequiredString("role"),
            reader.GetBooleanValue("enabled"),
            reader.GetUtcDateTime("created_at"),
            reader.GetUtcDateTime("updated_at"));
    }
}

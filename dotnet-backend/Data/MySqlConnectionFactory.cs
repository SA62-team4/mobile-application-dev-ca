using MySql.Data.MySqlClient;
using Wellness.Backup.Api.Configuration;

namespace Wellness.Backup.Api.Data;

/// <summary>
/// Opens MySQL connections against the same database used by Spring Boot.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class MySqlConnectionFactory
{
    private readonly BackendOptions _options;

    public MySqlConnectionFactory(BackendOptions options)
    {
        _options = options;
    }

    public async Task<MySqlConnection> OpenAsync(CancellationToken cancellationToken = default)
    {
        var connection = new MySqlConnection(_options.MySqlConnectionString);
        await connection.OpenAsync(cancellationToken);
        return connection;
    }
}

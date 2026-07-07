using MySql.Data.MySqlClient;

namespace Wellness.Backup.Api.Data;

/// <summary>
/// Creates Spring-compatible MySQL tables when the backup backend starts first.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class DatabaseSchemaInitializer
{
    private readonly MySqlConnectionFactory _connections;
    private readonly ILogger<DatabaseSchemaInitializer> _logger;

    public DatabaseSchemaInitializer(MySqlConnectionFactory connections, ILogger<DatabaseSchemaInitializer> logger)
    {
        _connections = connections;
        _logger = logger;
    }

    public async Task InitializeAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = await _connections.OpenAsync(cancellationToken);
        foreach (var statement in Statements)
        {
            await using var command = new MySqlCommand(statement, connection);
            await command.ExecuteNonQueryAsync(cancellationToken);
        }

        _logger.LogInformation(".NET backup backend schema compatibility check completed");
    }

    private static readonly string[] Statements =
    [
        """
        CREATE TABLE IF NOT EXISTS users (
          id BIGINT NOT NULL AUTO_INCREMENT,
          email VARCHAR(255) NOT NULL,
          password_hash VARCHAR(255) NOT NULL,
          display_name VARCHAR(255) NOT NULL,
          role VARCHAR(255) NOT NULL DEFAULT 'USER',
          enabled BOOLEAN NOT NULL DEFAULT TRUE,
          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
          updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
          PRIMARY KEY (id),
          UNIQUE KEY idx_users_email (email)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS wellness_records (
          id BIGINT NOT NULL AUTO_INCREMENT,
          user_id BIGINT NOT NULL,
          record_date DATE NOT NULL,
          sleep_hours DECIMAL(4,1) NOT NULL,
          exercise_type VARCHAR(255) NULL,
          exercise_minutes INT NOT NULL,
          mood_score INT NOT NULL,
          notes TEXT NULL,
          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
          updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
          PRIMARY KEY (id),
          KEY idx_records_user_date (user_id, record_date),
          CONSTRAINT fk_records_user FOREIGN KEY (user_id) REFERENCES users(id)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS chat_messages (
          id BIGINT NOT NULL AUTO_INCREMENT,
          user_id BIGINT NOT NULL,
          user_question TEXT NOT NULL,
          assistant_answer TEXT NOT NULL,
          source_summary TEXT NULL,
          model_name VARCHAR(255) NULL,
          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
          PRIMARY KEY (id),
          KEY idx_chat_user_created (user_id, created_at),
          CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES users(id)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS recommendations (
          id BIGINT NOT NULL AUTO_INCREMENT,
          user_id BIGINT NOT NULL,
          title VARCHAR(255) NOT NULL,
          trend_summary TEXT NOT NULL,
          recommendation_text TEXT NOT NULL,
          action_items TEXT NULL,
          generated_by VARCHAR(255) NOT NULL DEFAULT 'python-agent',
          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
          PRIMARY KEY (id),
          KEY idx_recommendations_user_created (user_id, created_at),
          CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users(id)
        )
        """
    ];
}

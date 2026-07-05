namespace Wellness.Backup.Api.Configuration;

/// <summary>
/// Runtime configuration for the optional .NET cold-standby backend.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class BackendOptions
{
    public required string MySqlConnectionString { get; init; }
    public required string JwtSecret { get; init; }
    public required long JwtExpirySeconds { get; init; }
    public required string AiServiceUrl { get; init; }
    public required string InternalServiceToken { get; init; }
    public string? LoginRedirectUrl { get; init; }

    public static BackendOptions FromConfiguration(IConfiguration configuration)
    {
        var explicitConnection = FirstNonBlank(
            configuration["DOTNET_CONNECTION_STRING"],
            configuration["MYSQL_CONNECTION_STRING"],
            configuration.GetConnectionString("MySql"));

        var connectionString = explicitConnection ?? BuildMySqlConnectionString(configuration);

        return new BackendOptions
        {
            MySqlConnectionString = connectionString,
            JwtSecret = FirstNonBlank(
                configuration["JWT_SECRET"],
                configuration["BackupBackend:JwtSecret"]) ?? "dev_secret_replace_with_very_long_random_value",
            JwtExpirySeconds = ParseLong(
                FirstNonBlank(configuration["JWT_EXPIRY_SECONDS"], configuration["BackupBackend:JwtExpirySeconds"]),
                86400),
            AiServiceUrl = (FirstNonBlank(configuration["AI_SERVICE_URL"], configuration["BackupBackend:AiServiceUrl"])
                           ?? "http://localhost:8000").TrimEnd('/'),
            InternalServiceToken = FirstNonBlank(
                configuration["INTERNAL_SERVICE_TOKEN"],
                configuration["BackupBackend:InternalServiceToken"]) ?? "dev_internal_token",
            LoginRedirectUrl = FirstNonBlank(
                configuration["LOGIN_REDIRECT_URL"],
                configuration["BackupBackend:LoginRedirectUrl"])
        };
    }

    private static string BuildMySqlConnectionString(IConfiguration configuration)
    {
        var host = FirstNonBlank(configuration["MYSQL_HOST"], "localhost");
        var port = FirstNonBlank(configuration["MYSQL_PORT"], configuration["MYSQL_HOST_PORT"], "3307");
        var database = FirstNonBlank(configuration["MYSQL_DATABASE"], "wellness_app");
        var user = FirstNonBlank(configuration["MYSQL_USER"], "wellness_user");
        var password = FirstNonBlank(configuration["MYSQL_PASSWORD"], "change_me");
        return $"Server={host};Port={port};Database={database};User={user};Password={password};TreatTinyAsBoolean=true;";
    }

    private static long ParseLong(string? value, long fallback)
    {
        return long.TryParse(value, out var parsed) ? parsed : fallback;
    }

    private static string? FirstNonBlank(params string?[] values)
    {
        return values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));
    }
}
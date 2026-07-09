using Microsoft.Extensions.Configuration;
using Wellness.Backup.Api.Configuration;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Covers <see cref="BackendOptions.FromConfiguration"/> precedence: explicit
/// connection strings, the assembled MySQL fallback, and defaulted secrets.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class BackendOptionsTests
{
    private static IConfiguration Config(params (string Key, string Value)[] entries)
    {
        var data = entries.ToDictionary(e => e.Key, e => (string?)e.Value);
        return new ConfigurationBuilder().AddInMemoryCollection(data).Build();
    }

    [Fact]
    public void FromConfiguration_UsesExplicitConnectionString()
    {
        var options = BackendOptions.FromConfiguration(Config(
            ("DOTNET_CONNECTION_STRING", "Server=explicit;Database=db;")));

        Assert.Equal("Server=explicit;Database=db;", options.MySqlConnectionString);
    }

    [Fact]
    public void FromConfiguration_BuildsConnectionStringFromParts()
    {
        var options = BackendOptions.FromConfiguration(Config(
            ("MYSQL_HOST", "db-host"),
            ("MYSQL_PORT", "3399"),
            ("MYSQL_DATABASE", "wellness"),
            ("MYSQL_USER", "svc"),
            ("MYSQL_PASSWORD", "secret")));

        Assert.Contains("Server=db-host;", options.MySqlConnectionString);
        Assert.Contains("Port=3399;", options.MySqlConnectionString);
        Assert.Contains("Database=wellness;", options.MySqlConnectionString);
        Assert.Contains("User=svc;", options.MySqlConnectionString);
        Assert.Contains("Password=secret;", options.MySqlConnectionString);
    }

    [Fact]
    public void FromConfiguration_AppliesDefaultsWhenUnset()
    {
        var options = BackendOptions.FromConfiguration(Config());

        Assert.Equal("dev_secret_replace_with_very_long_random_value", options.JwtSecret);
        Assert.Equal(86400, options.JwtExpirySeconds);
        Assert.Equal("http://localhost:8000", options.AiServiceUrl);
        Assert.Equal("dev_internal_token", options.InternalServiceToken);
        Assert.Equal("", options.GoogleClientId);
        Assert.Contains("Server=localhost;", options.MySqlConnectionString);
    }

    [Fact]
    public void FromConfiguration_ReadsOverridesAndTrimsAiUrl()
    {
        var options = BackendOptions.FromConfiguration(Config(
            ("JWT_SECRET", "custom-secret"),
            ("JWT_EXPIRY_SECONDS", "3600"),
            ("AI_SERVICE_URL", "http://ai:9000/"),
            ("INTERNAL_SERVICE_TOKEN", "tok"),
            ("GOOGLE_CLIENT_ID", "google-client")));

        Assert.Equal("custom-secret", options.JwtSecret);
        Assert.Equal(3600, options.JwtExpirySeconds);
        Assert.Equal("http://ai:9000", options.AiServiceUrl);
        Assert.Equal("tok", options.InternalServiceToken);
        Assert.Equal("google-client", options.GoogleClientId);
    }

    [Fact]
    public void FromConfiguration_FallsBackWhenExpiryNotNumeric()
    {
        var options = BackendOptions.FromConfiguration(Config(("JWT_EXPIRY_SECONDS", "not-a-number")));

        Assert.Equal(86400, options.JwtExpirySeconds);
    }
}

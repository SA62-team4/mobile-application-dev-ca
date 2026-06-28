// Author: SA62 Group 4 - Resolves the Spring Boot backend base URL (REQ-21).
using System;
using System.IO;
using System.Text.Json;

namespace WellnessDesktop;

/// <summary>
/// Loads the backend base URL from the WELLNESS_API_BASE_URL environment variable,
/// falling back to appsettings.json, then to a local default.
/// </summary>
public sealed class AppConfig
{
    private const string DefaultBaseUrl = "http://localhost:8080/";

    public Uri BackendBaseUri { get; }

    private AppConfig(string baseUrl)
    {
        // Ensure a trailing slash so relative request paths resolve correctly.
        if (!baseUrl.EndsWith('/'))
        {
            baseUrl += "/";
        }

        BackendBaseUri = new Uri(baseUrl, UriKind.Absolute);
    }

    public static AppConfig Load()
    {
        var fromEnv = Environment.GetEnvironmentVariable("WELLNESS_API_BASE_URL");
        if (!string.IsNullOrWhiteSpace(fromEnv))
        {
            return new AppConfig(fromEnv.Trim());
        }

        var fromFile = TryReadAppSettings();
        return new AppConfig(fromFile ?? DefaultBaseUrl);
    }

    private static string? TryReadAppSettings()
    {
        try
        {
            var path = Path.Combine(AppContext.BaseDirectory, "appsettings.json");
            if (!File.Exists(path))
            {
                return null;
            }

            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            if (doc.RootElement.TryGetProperty("BackendBaseUrl", out var value))
            {
                var text = value.GetString();
                return string.IsNullOrWhiteSpace(text) ? null : text;
            }
        }
        catch
        {
            // Fall back to the default if the config file is missing or malformed.
        }

        return null;
    }
}

using System.Text.Json;
using Wellness.Backup.Api.Errors;

namespace Wellness.Backup.Api.Middleware;

/// <summary>
/// Converts .NET exceptions into the same JSON error response shape used by Spring.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng, Chua Wei Yi Justin</remarks>
public sealed class ApiErrorMiddleware
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly RequestDelegate _next;
    private readonly ILogger<ApiErrorMiddleware> _logger;

    public ApiErrorMiddleware(RequestDelegate next, ILogger<ApiErrorMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (ApiException exception)
        {
            await WriteErrorAsync(context, exception.StatusCode, exception.Message);
        }
        catch (BadHttpRequestException exception)
        {
            _logger.LogWarning("Invalid API request: {Message}", exception.Message);
            await WriteErrorAsync(context, StatusCodes.Status400BadRequest, "Invalid request body");
        }
        catch (Exception exception)
        {
            _logger.LogError(exception, "Unexpected .NET backup backend error");
            await WriteErrorAsync(context, StatusCodes.Status500InternalServerError, "Unexpected server error");
        }
    }

    private static async Task WriteErrorAsync(HttpContext context, int statusCode, string message)
    {
        if (context.Response.HasStarted)
        {
            return;
        }

        context.Response.Clear();
        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/json";

        var response = new ApiErrorResponse(
            DateTimeOffset.UtcNow,
            statusCode,
            ReasonPhrase(statusCode),
            message,
            context.Request.Path.Value ?? string.Empty);

        await context.Response.WriteAsync(JsonSerializer.Serialize(response, JsonOptions));
    }

    private static string ReasonPhrase(int statusCode) => statusCode switch
    {
        StatusCodes.Status400BadRequest => "Bad Request",
        StatusCodes.Status401Unauthorized => "Unauthorized",
        StatusCodes.Status403Forbidden => "Forbidden",
        StatusCodes.Status404NotFound => "Not Found",
        StatusCodes.Status409Conflict => "Conflict",
        StatusCodes.Status503ServiceUnavailable => "Service Unavailable",
        StatusCodes.Status500InternalServerError => "Internal Server Error",
        _ => "Error"
    };
}

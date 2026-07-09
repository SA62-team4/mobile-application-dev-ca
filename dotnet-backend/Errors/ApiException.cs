namespace Wellness.Backup.Api.Errors;

/// <summary>
/// Controlled exception converted to the Spring-compatible JSON error shape.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng, Chua Wei Yi Justin</remarks>
public sealed class ApiException : Exception
{
    public ApiException(int statusCode, string message, long? retryAfterSeconds = null) : base(message)
    {
        StatusCode = statusCode;
        RetryAfterSeconds = retryAfterSeconds;
    }

    public int StatusCode { get; }

    /// <summary>Seconds until the caller may retry, surfaced as a Retry-After header (429 only).</summary>
    public long? RetryAfterSeconds { get; }

    public static ApiException BadRequest(string message) => new(StatusCodes.Status400BadRequest, message);

    public static ApiException TooManyRequests(string message, long retryAfterSeconds) =>
        new(StatusCodes.Status429TooManyRequests, message, retryAfterSeconds);

    public static ApiException Unauthorized(string message) => new(StatusCodes.Status401Unauthorized, message);

    public static ApiException Forbidden(string message) => new(StatusCodes.Status403Forbidden, message);

    public static ApiException NotFound(string message) => new(StatusCodes.Status404NotFound, message);

    public static ApiException Conflict(string message) => new(StatusCodes.Status409Conflict, message);

    public static ApiException ServiceUnavailable(string message) => new(StatusCodes.Status503ServiceUnavailable, message);
}

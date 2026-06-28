namespace Wellness.Backup.Api.Errors;

/// <summary>
/// Controlled exception converted to the Spring-compatible JSON error shape.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class ApiException : Exception
{
    public ApiException(int statusCode, string message) : base(message)
    {
        StatusCode = statusCode;
    }

    public int StatusCode { get; }

    public static ApiException BadRequest(string message) => new(StatusCodes.Status400BadRequest, message);

    public static ApiException Unauthorized(string message) => new(StatusCodes.Status401Unauthorized, message);

    public static ApiException Forbidden(string message) => new(StatusCodes.Status403Forbidden, message);

    public static ApiException NotFound(string message) => new(StatusCodes.Status404NotFound, message);

    public static ApiException Conflict(string message) => new(StatusCodes.Status409Conflict, message);

    public static ApiException ServiceUnavailable(string message) => new(StatusCodes.Status503ServiceUnavailable, message);
}

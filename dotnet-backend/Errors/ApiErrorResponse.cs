namespace Wellness.Backup.Api.Errors;

/// <summary>
/// Error payload matching the Spring Boot backend API contract.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed record ApiErrorResponse(
    DateTimeOffset Timestamp,
    int Status,
    string Error,
    string Message,
    string Path);

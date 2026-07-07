namespace Wellness.Backup.Api.Errors;

/// <summary>
/// Error payload matching the Spring Boot backend API contract.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed record ApiErrorResponse(
    DateTimeOffset Timestamp,
    int Status,
    string Error,
    string Message,
    string Path);

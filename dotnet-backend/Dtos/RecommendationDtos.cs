namespace Wellness.Backup.Api.Dtos;

/// <summary>
/// DTOs for recommendation endpoints mirrored from Spring Boot.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed record RecommendationResponse(
    long? Id,
    string Title,
    string TrendSummary,
    string RecommendationText,
    IReadOnlyList<string> ActionItems,
    string GeneratedBy,
    DateTime? CreatedAt);

public sealed record InternalRecommendationRequest(
    string? Title,
    string? TrendSummary,
    string? RecommendationText,
    IReadOnlyList<string>? ActionItems,
    string? GeneratedBy);

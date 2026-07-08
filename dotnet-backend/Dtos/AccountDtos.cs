namespace Wellness.Backup.Api.Dtos;

/// <summary>
/// DTOs for the privacy / account-management endpoints (S-03), mirrored from the
/// Spring Boot backend so the exported JSON is identical across both backends.
/// </summary>
/// <remarks>@author Chua Wei Yi Justin, Tiong Zhong Cheng</remarks>
public sealed record AccountUserProfile(
    long Id,
    string Email,
    string DisplayName,
    string Role,
    DateTime CreatedAt);

public sealed record ChatExport(
    long Id,
    string Question,
    string Answer,
    string? SourceSummary,
    string? ModelName,
    DateTime CreatedAt);

public sealed record AccountExport(
    AccountUserProfile Profile,
    IReadOnlyList<WellnessRecordResponse> WellnessRecords,
    IReadOnlyList<RecommendationResponse> Recommendations,
    IReadOnlyList<ChatExport> ChatMessages,
    DateTime ExportedAt);

/// <summary>
/// Optional password re-confirmation body for the permanent delete. Local
/// password accounts must provide the current password; SSO-only accounts have
/// no app password to re-enter, so the authenticated JWT is enough.
/// </summary>
public sealed record DeleteAccountRequest(string? Password);

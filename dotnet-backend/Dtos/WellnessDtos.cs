namespace Wellness.Backup.Api.Dtos;

/// <summary>
/// DTOs for wellness record endpoints mirrored from Spring Boot.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed record WellnessRecordRequest(
    DateOnly? RecordDate,
    decimal? SleepHours,
    string? ExerciseType,
    int? ExerciseMinutes,
    int? MoodScore,
    string? Notes);

public sealed record WellnessRecordResponse(
    long Id,
    DateOnly RecordDate,
    decimal SleepHours,
    string? ExerciseType,
    int ExerciseMinutes,
    int MoodScore,
    string? Notes,
    DateTime CreatedAt,
    DateTime UpdatedAt);

public sealed record RecentRecord(
    string RecordDate,
    double SleepHours,
    string? ExerciseType,
    int ExerciseMinutes,
    int MoodScore);

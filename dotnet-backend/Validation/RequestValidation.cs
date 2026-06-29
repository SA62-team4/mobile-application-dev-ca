using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;

namespace Wellness.Backup.Api.Validation;

/// <summary>
/// Minimal validation rules matching the public Spring API contract.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class RequestValidation
{
    public static void Validate(RegisterRequest request)
    {
        NotBlank(request.DisplayName, "Display name is required");
        Email(request.Email);
        if (string.IsNullOrWhiteSpace(request.Password))
        {
            throw ApiException.BadRequest("Password is required");
        }

        if (request.Password.Length < 8)
        {
            throw ApiException.BadRequest("Password must be at least 8 characters");
        }
    }

    public static void Validate(LoginRequest request)
    {
        Email(request.Email);
        NotBlank(request.Password, "Password is required");
    }

    public static void Validate(WellnessRecordRequest request)
    {
        if (request.RecordDate is null)
        {
            throw ApiException.BadRequest("Record date is required");
        }

        if (request.SleepHours is null or < 0 or > 24)
        {
            throw ApiException.BadRequest("Sleep hours must be between 0 and 24");
        }

        if (request.ExerciseMinutes is null or < 0)
        {
            throw ApiException.BadRequest("Exercise minutes must be zero or greater");
        }

        if (request.MoodScore is null or < 1 or > 5)
        {
            throw ApiException.BadRequest("Mood score must be between 1 and 5");
        }
    }

    public static void Validate(ChatRequest request)
    {
        NotBlank(request.Question, "Question is required");
    }

    public static void Validate(InternalRecommendationRequest request)
    {
        NotBlank(request.Title, "Title is required");
        NotBlank(request.TrendSummary, "Trend summary is required");
        NotBlank(request.RecommendationText, "Recommendation text is required");
    }

    private static void Email(string? value)
    {
        NotBlank(value, "Email is required");
        if (!value!.Contains('@', StringComparison.Ordinal))
        {
            throw ApiException.BadRequest("Email must be valid");
        }
    }

    private static void NotBlank(string? value, string message)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw ApiException.BadRequest(message);
        }
    }
}

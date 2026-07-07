// @author Tiong Zhong Cheng
// JSON uses camelCase; the shared JsonSerializerOptions in ApiClient maps these
// PascalCase properties automatically, so no per-property attributes are needed.
using System.Collections.Generic;

namespace WellnessDesktop.Models;

// --- Auth ---

public sealed class RegisterRequest
{
    public string DisplayName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public sealed class LoginRequest
{
    public string Email { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public sealed class UserDto
{
    public long Id { get; set; }
    public string DisplayName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
}

public sealed class LoginResponse
{
    public string Token { get; set; } = string.Empty;
    public string TokenType { get; set; } = "Bearer";
    public long ExpiresInSeconds { get; set; }
    public UserDto? User { get; set; }
}

// --- Wellness records ---

public sealed class WellnessRecordRequest
{
    public string RecordDate { get; set; } = string.Empty;
    public double SleepHours { get; set; }
    public string ExerciseType { get; set; } = string.Empty;
    public int ExerciseMinutes { get; set; }
    public int MoodScore { get; set; }
    public string? Notes { get; set; }
}

public sealed class WellnessRecordDto
{
    public long Id { get; set; }
    public string RecordDate { get; set; } = string.Empty;
    public double SleepHours { get; set; }
    public string ExerciseType { get; set; } = string.Empty;
    public int ExerciseMinutes { get; set; }
    public int MoodScore { get; set; }
    public string? Notes { get; set; }
    public string? CreatedAt { get; set; }
    public string? UpdatedAt { get; set; }
}

// --- Chatbot ---

public sealed class ChatRequest
{
    public string Question { get; set; } = string.Empty;
}

public sealed class ChatSourceDto
{
    public string Title { get; set; } = string.Empty;
    public string Snippet { get; set; } = string.Empty;
}

public sealed class ChatMessageDto
{
    public long Id { get; set; }
    public string Question { get; set; } = string.Empty;
    public string Answer { get; set; } = string.Empty;
    public List<ChatSourceDto> Sources { get; set; } = new();
    public string? ModelName { get; set; }
    public string? CreatedAt { get; set; }
}

// --- Recommendations ---

public sealed class RecommendationDto
{
    public long Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public string TrendSummary { get; set; } = string.Empty;
    public string RecommendationText { get; set; } = string.Empty;
    public List<string> ActionItems { get; set; } = new();
    public string? GeneratedBy { get; set; }
    public string? CreatedAt { get; set; }
}

// --- Errors ---

public sealed class ApiError
{
    public string? Timestamp { get; set; }
    public int Status { get; set; }
    public string? Error { get; set; }
    public string? Message { get; set; }
    public string? Path { get; set; }
}

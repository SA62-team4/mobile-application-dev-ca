using Microsoft.AspNetCore.Http;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Validation;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Exercises every validation rule (happy path and each rejection branch) so the
/// Spring-compatible 400 responses stay in lockstep with the public contract.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class RequestValidationTests
{
    private static void AssertBadRequest(Action act, string expectedMessage)
    {
        var exception = Assert.Throws<ApiException>(act);
        Assert.Equal(StatusCodes.Status400BadRequest, exception.StatusCode);
        Assert.Equal(expectedMessage, exception.Message);
    }

    [Fact]
    public void Register_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new RegisterRequest("Ada", "ada@example.com", "password1"));
    }

    [Theory]
    [InlineData(null, "ada@example.com", "password1", "Display name is required")]
    [InlineData(" ", "ada@example.com", "password1", "Display name is required")]
    [InlineData("Ada", null, "password1", "Email is required")]
    [InlineData("Ada", "no-at-symbol", "password1", "Email must be valid")]
    [InlineData("Ada", "ada@example.com", null, "Password is required")]
    [InlineData("Ada", "ada@example.com", "short", "Password must be at least 8 characters")]
    public void Register_Invalid_ThrowsBadRequest(string? name, string? email, string? password, string message)
    {
        AssertBadRequest(() => RequestValidation.Validate(new RegisterRequest(name, email, password)), message);
    }

    [Fact]
    public void Login_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new LoginRequest("ada@example.com", "password1"));
    }

    [Theory]
    [InlineData(null, "password1", "Email is required")]
    [InlineData("ada@example.com", " ", "Password is required")]
    public void Login_Invalid_ThrowsBadRequest(string? email, string? password, string message)
    {
        AssertBadRequest(() => RequestValidation.Validate(new LoginRequest(email, password)), message);
    }

    [Fact]
    public void GoogleAuth_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new GoogleAuthRequest("id-token"));
    }

    [Fact]
    public void GoogleAuth_Blank_ThrowsBadRequest()
    {
        AssertBadRequest(
            () => RequestValidation.Validate(new GoogleAuthRequest(" ")),
            "Google ID token is required");
    }

    [Fact]
    public void WellnessRecord_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new WellnessRecordRequest(
            DateOnly.FromDateTime(DateTime.UtcNow), 7.5m, "run", 30, 4, "note"));
    }

    [Theory]
    [InlineData(false, 7, 30, 3, "Record date is required")]
    [InlineData(true, -1, 30, 3, "Sleep hours must be between 0 and 24")]
    [InlineData(true, 25, 30, 3, "Sleep hours must be between 0 and 24")]
    [InlineData(true, 7, -5, 3, "Exercise minutes must be zero or greater")]
    [InlineData(true, 7, 30, 0, "Mood score must be between 1 and 5")]
    [InlineData(true, 7, 30, 6, "Mood score must be between 1 and 5")]
    public void WellnessRecord_Invalid_ThrowsBadRequest(
        bool hasDate, int sleep, int exercise, int mood, string message)
    {
        var request = new WellnessRecordRequest(
            hasDate ? DateOnly.FromDateTime(DateTime.UtcNow) : null,
            sleep, "run", exercise, mood, null);
        AssertBadRequest(() => RequestValidation.Validate(request), message);
    }

    [Fact]
    public void Chat_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new ChatRequest("How do I sleep better?"));
    }

    [Fact]
    public void Chat_Blank_ThrowsBadRequest()
    {
        AssertBadRequest(() => RequestValidation.Validate(new ChatRequest("  ")), "Question is required");
    }

    [Fact]
    public void InternalRecommendation_Valid_DoesNotThrow()
    {
        RequestValidation.Validate(new InternalRecommendationRequest(
            "Title", "Trend", "Do this", ["step"], "agent"));
    }

    [Theory]
    [InlineData(null, "Trend", "Text", "Title is required")]
    [InlineData("Title", null, "Text", "Trend summary is required")]
    [InlineData("Title", "Trend", " ", "Recommendation text is required")]
    public void InternalRecommendation_Invalid_ThrowsBadRequest(
        string? title, string? trend, string? text, string message)
    {
        var request = new InternalRecommendationRequest(title, trend, text, ["step"], "agent");
        AssertBadRequest(() => RequestValidation.Validate(request), message);
    }
}

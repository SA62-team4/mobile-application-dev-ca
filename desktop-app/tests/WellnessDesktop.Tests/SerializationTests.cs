// Author: SA62 Group 4 - DTO (de)serialization tests against the documented JSON (REQ-21).
using System.Text.Json;
using WellnessDesktop.Models;
using WellnessDesktop.Services;
using Xunit;

namespace WellnessDesktop.Tests;

public class SerializationTests
{
    private static readonly JsonSerializerOptions Options = ApiClient.JsonOptions;

    [Fact]
    public void ChatMessage_DeserializesAnswerAndSources()
    {
        const string json = """
        {"id":25,"question":"How can I sleep better?","answer":"Keep a routine.",
         "sources":[{"title":"Sleep Hygiene Basics","snippet":"Consistent bedtime helps."}],
         "modelName":"llama3.2:3b","createdAt":"2026-07-01T12:10:00Z"}
        """;

        var message = JsonSerializer.Deserialize<ChatMessageDto>(json, Options)!;

        Assert.Equal(25, message.Id);
        Assert.Equal("Keep a routine.", message.Answer);
        var source = Assert.Single(message.Sources);
        Assert.Equal("Sleep Hygiene Basics", source.Title);
        Assert.Equal("llama3.2:3b", message.ModelName);
    }

    [Fact]
    public void Recommendation_DeserializesActionItems()
    {
        const string json = """
        {"id":8,"title":"Improve sleep consistency","trendSummary":"Sleep varied.",
         "recommendationText":"Aim for a consistent bedtime.",
         "actionItems":["Set a fixed bedtime","Walk before 8pm"],
         "generatedBy":"python-agent","createdAt":"2026-07-01T12:20:00Z"}
        """;

        var rec = JsonSerializer.Deserialize<RecommendationDto>(json, Options)!;

        Assert.Equal("Improve sleep consistency", rec.Title);
        Assert.Equal(2, rec.ActionItems.Count);
        Assert.Equal("python-agent", rec.GeneratedBy);
    }

    [Fact]
    public void WellnessRecordRequest_SerializesCamelCase()
    {
        var request = new WellnessRecordRequest
        {
            RecordDate = "2026-07-01",
            SleepHours = 7.5,
            ExerciseType = "Walking",
            ExerciseMinutes = 30,
            MoodScore = 4,
            Notes = "Felt good."
        };

        var json = JsonSerializer.Serialize(request, Options);

        Assert.Contains("\"recordDate\":\"2026-07-01\"", json);
        Assert.Contains("\"sleepHours\":7.5", json);
        Assert.Contains("\"moodScore\":4", json);
    }
}

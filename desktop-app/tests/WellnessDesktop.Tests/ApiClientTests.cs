// Author: Tiong Zhong Cheng - ApiClient request/header/error behavior tests (REQ-21, NFR-02).
using System;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Threading.Tasks;
using WellnessDesktop.Models;
using WellnessDesktop.Services;
using Xunit;

namespace WellnessDesktop.Tests;

public class ApiClientTests
{
    private static ApiClient CreateClient(StubHttpMessageHandler handler, SessionStore session)
    {
        var http = new HttpClient(handler) { BaseAddress = new Uri("http://localhost:8080/") };
        return new ApiClient(http, session);
    }

    [Fact]
    public async Task LoginAsync_PostsToLoginRoute_WithoutAuthHeader_AndStoresToken()
    {
        const string body = """
        {"token":"jwt-token","tokenType":"Bearer","expiresInSeconds":86400,
         "user":{"id":1,"displayName":"Asha Tan","email":"asha@example.com"}}
        """;
        var handler = new StubHttpMessageHandler(HttpStatusCode.OK, body);
        var session = new SessionStore();
        var client = CreateClient(handler, session);

        var result = await client.LoginAsync(new LoginRequest { Email = "asha@example.com", Password = "pw" });

        var request = Assert.Single(handler.Requests);
        Assert.Equal(HttpMethod.Post, request.Method);
        Assert.Equal("http://localhost:8080/api/auth/login", request.RequestUri!.ToString());
        Assert.False(request.Headers.Contains("Authorization"));
        Assert.Equal("jwt-token", result.Token);
        Assert.True(session.IsAuthenticated);
        Assert.Equal("Asha Tan", session.CurrentUser!.DisplayName);
    }

    [Fact]
    public async Task GetRecordsAsync_AttachesBearerToken_WhenAuthenticated()
    {
        var handler = new StubHttpMessageHandler(HttpStatusCode.OK, "[]");
        var session = new SessionStore();
        session.SignIn("abc123", new UserDto { Id = 1 });
        var client = CreateClient(handler, session);

        await client.GetRecordsAsync();

        var request = Assert.Single(handler.Requests);
        Assert.Equal("http://localhost:8080/api/wellness-records", request.RequestUri!.ToString());
        Assert.Equal("Bearer abc123", request.Headers.GetValues("Authorization").Single());
    }

    [Fact]
    public async Task CreateRecordAsync_PostsToRecordsRoute()
    {
        const string body = """
        {"id":10,"recordDate":"2026-07-01","sleepHours":7.5,"exerciseType":"Walking",
         "exerciseMinutes":30,"moodScore":4,"notes":"ok"}
        """;
        var handler = new StubHttpMessageHandler(HttpStatusCode.Created, body);
        var session = new SessionStore();
        session.SignIn("abc123", null);
        var client = CreateClient(handler, session);

        var created = await client.CreateRecordAsync(new WellnessRecordRequest
        {
            RecordDate = "2026-07-01",
            SleepHours = 7.5,
            ExerciseType = "Walking",
            ExerciseMinutes = 30,
            MoodScore = 4
        });

        var request = Assert.Single(handler.Requests);
        Assert.Equal(HttpMethod.Post, request.Method);
        Assert.Equal("http://localhost:8080/api/wellness-records", request.RequestUri!.ToString());
        Assert.Equal(10, created.Id);
        Assert.Equal("Walking", created.ExerciseType);
    }

    [Fact]
    public async Task NonSuccess_ParsesStandardErrorMessage_IntoApiException()
    {
        const string error = """
        {"timestamp":"2026-07-01T10:30:00Z","status":400,"error":"Bad Request",
         "message":"Mood score must be between 1 and 5","path":"/api/wellness-records"}
        """;
        var handler = new StubHttpMessageHandler(HttpStatusCode.BadRequest, error);
        var client = CreateClient(handler, new SessionStore());

        var ex = await Assert.ThrowsAsync<ApiException>(
            () => client.GetRecordsAsync());

        Assert.Equal("Mood score must be between 1 and 5", ex.Message);
        Assert.Equal(400, ex.StatusCode);
    }

    [Fact]
    public async Task LogoutAsync_ClearsSession_EvenOnServerError()
    {
        var handler = new StubHttpMessageHandler(HttpStatusCode.InternalServerError, "{}");
        var session = new SessionStore();
        session.SignIn("abc123", new UserDto { Id = 1 });
        var client = CreateClient(handler, session);

        await Assert.ThrowsAsync<ApiException>(() => client.LogoutAsync());

        Assert.False(session.IsAuthenticated);
        Assert.Null(session.Token);
    }
}

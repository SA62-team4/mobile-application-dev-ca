using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the AI service client maps success payloads and turns any transport
/// or non-success response into a 503 ApiException, using a stub HTTP handler so
/// no live Python service is required.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class AiServiceClientTests
{
    private sealed class StubHandler(Func<HttpRequestMessage, HttpResponseMessage> responder) : HttpMessageHandler
    {
        public HttpRequestMessage? LastRequest { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastRequest = request;
            return Task.FromResult(responder(request));
        }
    }

    private static BackendOptions Options() => new()
    {
        MySqlConnectionString = "Server=localhost;",
        JwtSecret = "unit_test_secret_value_with_at_least_32_chars",
        JwtExpirySeconds = 3600,
        AiServiceUrl = "http://localhost:8000",
        InternalServiceToken = "test-token"
    };

    private static AiServiceClient Client(StubHandler handler) =>
        new(new HttpClient(handler), Options(), NullLogger<AiServiceClient>.Instance);

    [Fact]
    public async Task ChatAsync_ReturnsParsedResponse()
    {
        var payload = new AiChatResponse("Sleep 8 hours", [new SourceSnippet("Sleep", "rest well")], "test-model");
        var handler = new StubHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = JsonContent.Create(payload)
        });
        var client = Client(handler);

        var result = await client.ChatAsync(new AiChatRequest(1, "How do I sleep?", []), CancellationToken.None);

        Assert.Equal("Sleep 8 hours", result.Answer);
        Assert.Equal("test-model", result.ModelName);
        Assert.Equal("/rag/chat", handler.LastRequest!.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task ChatAsync_NonSuccess_ThrowsServiceUnavailable()
    {
        var handler = new StubHandler(_ => new HttpResponseMessage(HttpStatusCode.BadGateway));
        var exception = await Assert.ThrowsAsync<ApiException>(() =>
            Client(handler).ChatAsync(new AiChatRequest(1, "q", []), CancellationToken.None));

        Assert.Equal(StatusCodes.Status503ServiceUnavailable, exception.StatusCode);
    }

    [Fact]
    public async Task ChatAsync_TransportFailure_ThrowsServiceUnavailable()
    {
        var handler = new StubHandler(_ => throw new HttpRequestException("boom"));
        var exception = await Assert.ThrowsAsync<ApiException>(() =>
            Client(handler).ChatAsync(new AiChatRequest(1, "q", []), CancellationToken.None));

        Assert.Equal(StatusCodes.Status503ServiceUnavailable, exception.StatusCode);
    }

    [Fact]
    public async Task GenerateRecommendationAsync_ReturnsParsedResponse()
    {
        var payload = new RecommendationResponse(
            5, "Title", "Trend", "Do this", ["step"], "agent", DateTime.UtcNow);
        var handler = new StubHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = JsonContent.Create(payload)
        });
        var client = Client(handler);

        var result = await client.GenerateRecommendationAsync(42, CancellationToken.None);

        Assert.Equal("Title", result.Title);
        Assert.Equal("/agent/recommendation/42", handler.LastRequest!.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task GenerateRecommendationAsync_NonSuccess_ThrowsServiceUnavailable()
    {
        var handler = new StubHandler(_ => new HttpResponseMessage(HttpStatusCode.InternalServerError));
        var exception = await Assert.ThrowsAsync<ApiException>(() =>
            Client(handler).GenerateRecommendationAsync(1, CancellationToken.None));

        Assert.Equal(StatusCodes.Status503ServiceUnavailable, exception.StatusCode);
    }
}

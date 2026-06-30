using System.Net.Http.Json;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Dtos;
using Wellness.Backup.Api.Errors;

namespace Wellness.Backup.Api.Services;

/// <summary>
/// HTTP client for the existing Python RAG and agentic AI service.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class AiServiceClient
{
    private readonly HttpClient _httpClient;
    private readonly BackendOptions _options;
    private readonly ILogger<AiServiceClient> _logger;

    public AiServiceClient(HttpClient httpClient, BackendOptions options, ILogger<AiServiceClient> logger)
    {
        _httpClient = httpClient;
        _options = options;
        _logger = logger;
        _httpClient.BaseAddress = new Uri(_options.AiServiceUrl);
        _httpClient.Timeout = TimeSpan.FromSeconds(30);
    }

    public async Task<AiChatResponse> ChatAsync(AiChatRequest request, CancellationToken cancellationToken)
    {
        try
        {
            var response = await _httpClient.PostAsJsonAsync("/rag/chat", request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                _logger.LogWarning("AI chatbot service returned {StatusCode}", response.StatusCode);
                throw ApiException.ServiceUnavailable("AI chatbot service is unavailable");
            }

            return await response.Content.ReadFromJsonAsync<AiChatResponse>(cancellationToken: cancellationToken)
                   ?? throw ApiException.ServiceUnavailable("AI chatbot service is unavailable");
        }
        catch (ApiException)
        {
            throw;
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, "AI chatbot service request failed");
            throw ApiException.ServiceUnavailable("AI chatbot service is unavailable");
        }
    }

    public async Task<RecommendationResponse> GenerateRecommendationAsync(long userId, CancellationToken cancellationToken)
    {
        try
        {
            var response = await _httpClient.PostAsync($"/agent/recommendation/{userId}", null, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                _logger.LogWarning("AI recommendation service returned {StatusCode}", response.StatusCode);
                throw ApiException.ServiceUnavailable("AI recommendation service is unavailable");
            }

            return await response.Content.ReadFromJsonAsync<RecommendationResponse>(cancellationToken: cancellationToken)
                   ?? throw ApiException.ServiceUnavailable("AI recommendation service is unavailable");
        }
        catch (ApiException)
        {
            throw;
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, "AI recommendation service request failed");
            throw ApiException.ServiceUnavailable("AI recommendation service is unavailable");
        }
    }
}

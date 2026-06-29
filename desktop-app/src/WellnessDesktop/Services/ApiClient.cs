// Author: SA62 Group 4 - HttpClient-based REST client for the Spring Boot backend (REQ-21).
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;
using System.Threading.Tasks;
using WellnessDesktop.Models;

namespace WellnessDesktop.Services;

/// <summary>
/// Calls the Spring Boot REST API, attaching the bearer JWT from the
/// <see cref="SessionStore"/> on every non-auth request. Non-success responses are
/// translated into a user-friendly <see cref="ApiException"/> (NFR-02).
/// </summary>
public sealed class ApiClient : IApiClient
{
    public static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };

    private readonly HttpClient _http;
    private readonly SessionStore _session;

    public ApiClient(HttpClient http, SessionStore session)
    {
        _http = http;
        _session = session;
    }

    // --- Auth ---

    public Task<UserDto> RegisterAsync(RegisterRequest request) =>
        SendAsync<UserDto>(HttpMethod.Post, "api/auth/register", request, authenticated: false);

    public async Task<LoginResponse> LoginAsync(LoginRequest request)
    {
        var response = await SendAsync<LoginResponse>(
            HttpMethod.Post, "api/auth/login", request, authenticated: false);
        _session.SignIn(response.Token, response.User);
        return response;
    }

    public async Task LogoutAsync()
    {
        try
        {
            await SendAsync<object?>(HttpMethod.Post, "api/auth/logout", body: null, authenticated: true);
        }
        finally
        {
            // Logout is stateless; always clear the local session.
            _session.SignOut();
        }
    }

    // --- Wellness records ---

    public Task<IReadOnlyList<WellnessRecordDto>> GetRecordsAsync(string? from = null, string? to = null)
    {
        var query = new List<string>();
        if (!string.IsNullOrWhiteSpace(from)) query.Add("from=" + Uri.EscapeDataString(from));
        if (!string.IsNullOrWhiteSpace(to)) query.Add("to=" + Uri.EscapeDataString(to));
        var path = "api/wellness-records" + (query.Count > 0 ? "?" + string.Join("&", query) : string.Empty);
        return SendListAsync<WellnessRecordDto>(HttpMethod.Get, path);
    }

    public Task<WellnessRecordDto> CreateRecordAsync(WellnessRecordRequest request) =>
        SendAsync<WellnessRecordDto>(HttpMethod.Post, "api/wellness-records", request);

    public Task<WellnessRecordDto> UpdateRecordAsync(long id, WellnessRecordRequest request) =>
        SendAsync<WellnessRecordDto>(HttpMethod.Put, $"api/wellness-records/{id}", request);

    public Task DeleteRecordAsync(long id) =>
        SendAsync<object?>(HttpMethod.Delete, $"api/wellness-records/{id}", body: null);

    // --- Chatbot ---

    public Task<ChatMessageDto> AskChatAsync(string question) =>
        SendAsync<ChatMessageDto>(HttpMethod.Post, "api/chat/messages", new ChatRequest { Question = question });

    public Task<IReadOnlyList<ChatMessageDto>> GetChatHistoryAsync() =>
        SendListAsync<ChatMessageDto>(HttpMethod.Get, "api/chat/messages");

    // --- Recommendations ---

    public Task<RecommendationDto> GenerateRecommendationAsync() =>
        SendAsync<RecommendationDto>(HttpMethod.Post, "api/recommendations/generate", body: null);

    public Task<IReadOnlyList<RecommendationDto>> GetRecommendationsAsync() =>
        SendListAsync<RecommendationDto>(HttpMethod.Get, "api/recommendations");

    // --- Core request pipeline ---

    private async Task<T> SendAsync<T>(HttpMethod method, string path, object? body, bool authenticated = true)
    {
        using var request = BuildRequest(method, path, body, authenticated);
        using var response = await _http.SendAsync(request);
        await EnsureSuccessAsync(response);

        if (typeof(T) == typeof(object))
        {
            return default!; // No body expected (e.g. logout, delete).
        }

        var result = await response.Content.ReadFromJsonAsync<T>(JsonOptions);
        return result ?? throw new ApiException("The server returned an empty response.");
    }

    private async Task<IReadOnlyList<T>> SendListAsync<T>(HttpMethod method, string path)
    {
        using var request = BuildRequest(method, path, body: null, authenticated: true);
        using var response = await _http.SendAsync(request);
        await EnsureSuccessAsync(response);

        var result = await response.Content.ReadFromJsonAsync<List<T>>(JsonOptions);
        return result ?? new List<T>();
    }

    private HttpRequestMessage BuildRequest(HttpMethod method, string path, object? body, bool authenticated)
    {
        var request = new HttpRequestMessage(method, path);

        if (authenticated && !string.IsNullOrEmpty(_session.Token))
        {
            request.Headers.TryAddWithoutValidation("Authorization", "Bearer " + _session.Token);
        }

        if (body is not null)
        {
            request.Content = JsonContent.Create(body, body.GetType(), options: JsonOptions);
        }

        return request;
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        var message = $"Request failed ({(int)response.StatusCode}).";
        try
        {
            var error = await response.Content.ReadFromJsonAsync<ApiError>(JsonOptions);
            if (!string.IsNullOrWhiteSpace(error?.Message))
            {
                message = error!.Message!;
            }
        }
        catch
        {
            // Body was not the standard error shape; keep the generic message.
        }

        throw new ApiException(message, (int)response.StatusCode);
    }
}

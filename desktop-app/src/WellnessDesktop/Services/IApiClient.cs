// Author: SA62 Group 4 - REST client contract for the Spring Boot backend (REQ-21).
using System.Collections.Generic;
using System.Threading.Tasks;
using WellnessDesktop.Models;

namespace WellnessDesktop.Services;

/// <summary>
/// Typed access to the Spring Boot REST API documented in
/// docs/specs/06-plan-api-contracts.md. The desktop client calls only this API.
/// </summary>
public interface IApiClient
{
    Task<UserDto> RegisterAsync(RegisterRequest request);
    Task<LoginResponse> LoginAsync(LoginRequest request);
    Task LogoutAsync();

    Task<IReadOnlyList<WellnessRecordDto>> GetRecordsAsync(string? from = null, string? to = null);
    Task<WellnessRecordDto> CreateRecordAsync(WellnessRecordRequest request);
    Task<WellnessRecordDto> UpdateRecordAsync(long id, WellnessRecordRequest request);
    Task DeleteRecordAsync(long id);

    Task<ChatMessageDto> AskChatAsync(string question);
    Task<IReadOnlyList<ChatMessageDto>> GetChatHistoryAsync();

    Task<RecommendationDto> GenerateRecommendationAsync();
    Task<IReadOnlyList<RecommendationDto>> GetRecommendationsAsync();
}

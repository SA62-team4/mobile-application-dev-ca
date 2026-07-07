namespace Wellness.Backup.Api.Dtos;

/// <summary>
/// DTOs for RAG chatbot endpoints mirrored from Spring Boot.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed record ChatRequest(string? Question);

public sealed record SourceSnippet(string Title, string Snippet);

public sealed record ChatResponse(
    long Id,
    string Question,
    string Answer,
    IReadOnlyList<SourceSnippet> Sources,
    string? ModelName,
    DateTime CreatedAt);

public sealed record AiChatRequest(long UserId, string Question, IReadOnlyList<RecentRecord> RecentRecords);

public sealed record AiChatResponse(string Answer, IReadOnlyList<SourceSnippet> Sources, string ModelName);

namespace Wellness.Backup.Api.Dtos;

/// <summary>
/// DTOs for authentication endpoints mirrored from Spring Boot.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed record RegisterRequest(string? DisplayName, string? Email, string? Password);

public sealed record LoginRequest(string? Email, string? Password);

public sealed record UserResponse(long Id, string DisplayName, string Email);

public sealed record LoginResponse(string Token, string TokenType, long ExpiresInSeconds, UserResponse User);

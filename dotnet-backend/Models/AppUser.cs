namespace Wellness.Backup.Api.Models;

/// <summary>
/// User row from the shared MySQL schema.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed record AppUser(
    long Id,
    string Email,
    string PasswordHash,
    string DisplayName,
    string Role,
    bool Enabled,
    DateTime CreatedAt,
    DateTime UpdatedAt);

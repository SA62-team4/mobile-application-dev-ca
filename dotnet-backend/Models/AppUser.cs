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
    Role Role,
    bool Enabled,
    DateTime CreatedAt,
    DateTime UpdatedAt)
{
    /// <summary>
    /// Authorities granted to this user, derived from its <see cref="Models.Role"/>.
    /// A user is granted exactly the authority of its own role; higher tiers such as
    /// PREMIUM_USER are intentionally not expanded into USER until premium auth is wired up.
    /// </summary>
    public IReadOnlyCollection<string> GrantedAuthorities() => new[] { this.Role.ToAuthority() };

    /// <summary>
    /// Whether this user satisfies a required role for an authorization gate. Mirrors
    /// Spring's <c>hasRole</c>: the required role's authority must be among those granted.
    /// </summary>
    public bool HasRole(Role required) => GrantedAuthorities().Contains(required.ToAuthority());
}
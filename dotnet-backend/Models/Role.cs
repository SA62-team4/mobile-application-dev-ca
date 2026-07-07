namespace Wellness.Backup.Api.Models;

/// <summary>
/// Account roles used for authentication and authorization.
/// </summary>
/// <remarks>
/// Wire values match Spring's shared MySQL/JWT role strings.
/// @author Chua Wei Yi Justin
/// </remarks>
public enum Role
{
    User,
    PremiumUser
}

/// <summary>
/// Maps roles to storage values and Spring-style authorities.
/// </summary>
/// <remarks>@author Chua Wei Yi Justin</remarks>
public static class RoleExtensions
{
    /// <summary>Spring Security prefixes role authorities with <c>ROLE_</c>.</summary>
    public const string AuthorityPrefix = "ROLE_";

    /// <summary>Persisted/JWT value.</summary>
    public static string ToDbValue(this Role role) => role switch
    {
        Role.User => "USER",
        Role.PremiumUser => "PREMIUM_USER",
        _ => "USER"
    };

    /// <summary>
    /// The Spring Security authority string, e.g. <see cref="Role.User"/> => <c>"ROLE_USER"</c>.
    /// </summary>
    public static string ToAuthority(this Role role) => AuthorityPrefix + role.ToDbValue();

    /// <summary>
    /// Parses a stored/claim value into a <see cref="Role"/>, defaulting to
    /// <see cref="Role.User"/> when the value is missing or unrecognised so legacy
    /// rows never break login.
    /// </summary>
    public static Role FromDbValue(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return Role.User;
        }

        return value.Trim().ToUpperInvariant() switch
        {
            "USER" => Role.User,
            "PREMIUM_USER" => Role.PremiumUser,
            _ => Role.User
        };
    }
}

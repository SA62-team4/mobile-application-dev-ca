namespace Wellness.Backup.Api.Models;

/// <summary>
/// Account roles used for authentication and authorization.
/// </summary>
/// <remarks>
/// The database/JWT wire value is the SCREAMING_SNAKE_CASE string ("USER" /
/// "PREMIUM_USER") produced by <see cref="RoleExtensions.ToDbValue"/>. These match
/// the values the Spring Boot backend persists in the shared <c>users.role</c>
/// column and carries in the JWT <c>role</c> claim, keeping both backends
/// interoperable against the one MySQL schema.
/// <para>
/// <see cref="PremiumUser"/> is declared for forward compatibility only; it is not
/// yet granted or checked anywhere in the authentication/authorization flow.
/// </para>
/// @author JustinChua97
/// </remarks>
public enum Role
{
    User,
    PremiumUser
}

/// <summary>
/// Maps <see cref="Role"/> to and from its canonical storage/wire string and
/// Spring-style authority ("ROLE_USER").
/// </summary>
/// <remarks>@author JustinChua97</remarks>
public static class RoleExtensions
{
    /// <summary>Spring Security prefixes role authorities with <c>ROLE_</c>.</summary>
    public const string AuthorityPrefix = "ROLE_";

    /// <summary>
    /// The canonical persisted/claim value, e.g. <see cref="Role.User"/> => <c>"USER"</c>.
    /// </summary>
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

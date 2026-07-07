namespace Wellness.Backup.Api.Services;

/// <summary>
/// BCrypt hashing compatible with Spring Security's BCryptPasswordEncoder.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class PasswordService
{
    public string Hash(string password)
    {
        return BCrypt.Net.BCrypt.HashPassword(password);
    }

    public bool Verify(string password, string passwordHash)
    {
        return BCrypt.Net.BCrypt.Verify(password, passwordHash);
    }
}

using Wellness.Backup.Api.Models;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies role-derived authorities and the Spring-style hasRole gate on AppUser.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class AppUserTests
{
    private static AppUser NewUser(Role role) => new(
        1, "ada@example.com", "hash", "Ada", role, true, DateTime.UtcNow, DateTime.UtcNow);

    [Fact]
    public void GrantedAuthorities_ContainsOwnRoleAuthority()
    {
        var user = NewUser(Role.PremiumUser);
        Assert.Equal(["ROLE_PREMIUM_USER"], user.GrantedAuthorities());
    }

    [Fact]
    public void HasRole_TrueForOwnRole()
    {
        Assert.True(NewUser(Role.User).HasRole(Role.User));
    }

    [Fact]
    public void HasRole_FalseForDifferentRole()
    {
        // Premium is intentionally not expanded into USER until premium auth is wired up.
        Assert.False(NewUser(Role.PremiumUser).HasRole(Role.User));
    }
}

using Wellness.Backup.Api.Models;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Locks the role wire values and authorities to Spring's shared MySQL/JWT strings.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class RoleExtensionsTests
{
    [Theory]
    [InlineData(Role.User, "USER")]
    [InlineData(Role.PremiumUser, "PREMIUM_USER")]
    public void ToDbValue_MapsToStorageString(Role role, string expected)
    {
        Assert.Equal(expected, role.ToDbValue());
    }

    [Theory]
    [InlineData(Role.User, "ROLE_USER")]
    [InlineData(Role.PremiumUser, "ROLE_PREMIUM_USER")]
    public void ToAuthority_PrefixesWithRole(Role role, string expected)
    {
        Assert.Equal(expected, role.ToAuthority());
    }

    [Theory]
    [InlineData("USER", Role.User)]
    [InlineData("premium_user", Role.PremiumUser)]
    [InlineData("  PREMIUM_USER  ", Role.PremiumUser)]
    [InlineData("unknown", Role.User)]
    [InlineData(null, Role.User)]
    [InlineData("", Role.User)]
    public void FromDbValue_ParsesOrDefaultsToUser(string? value, Role expected)
    {
        Assert.Equal(expected, RoleExtensions.FromDbValue(value));
    }
}

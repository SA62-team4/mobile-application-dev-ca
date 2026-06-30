using Wellness.Backup.Api.Services;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies recommendation action items use Spring's newline storage format.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public sealed class ActionItemTextTests
{
    [Fact]
    public void JoinAndSplit_RoundTripActionItems()
    {
        var stored = ActionItemText.Join(["Set a fixed bedtime", "Take a 20 minute walk"]);

        Assert.Equal("Set a fixed bedtime\nTake a 20 minute walk", stored);
        Assert.Equal(["Set a fixed bedtime", "Take a 20 minute walk"], ActionItemText.Split(stored));
    }
}

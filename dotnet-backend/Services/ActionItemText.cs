namespace Wellness.Backup.Api.Services;

/// <summary>
/// Converts recommendation action items to the newline-delimited storage format used by Spring.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class ActionItemText
{
    public static string Join(IReadOnlyList<string>? actionItems)
    {
        return actionItems is null ? string.Empty : string.Join("\n", actionItems);
    }

    public static IReadOnlyList<string> Split(string? actionItems)
    {
        if (string.IsNullOrWhiteSpace(actionItems))
        {
            return [];
        }

        return actionItems
            .Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .ToList();
    }
}

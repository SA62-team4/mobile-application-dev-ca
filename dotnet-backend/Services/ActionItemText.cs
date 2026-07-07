namespace Wellness.Backup.Api.Services;

/// <summary>
/// Converts action items to Spring's newline-delimited format.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
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

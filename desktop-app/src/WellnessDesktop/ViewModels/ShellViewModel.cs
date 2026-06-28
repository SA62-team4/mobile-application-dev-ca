// Author: SA62 Group 4 - Signed-in shell hosting the feature tabs (REQ-21).
using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class ShellViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private readonly Action _onSignedOut;

    public RecordsViewModel Records { get; }
    public ChatViewModel Chat { get; }
    public RecommendationsViewModel Recommendations { get; }

    [ObservableProperty] private int _selectedIndex;

    public string WelcomeText { get; }

    public ShellViewModel(IApiClient api, SessionStore session, Action onSignedOut)
    {
        _api = api;
        _onSignedOut = onSignedOut;

        Records = new RecordsViewModel(api);
        Chat = new ChatViewModel(api);
        Recommendations = new RecommendationsViewModel(api);

        var name = session.CurrentUser?.DisplayName;
        WelcomeText = string.IsNullOrWhiteSpace(name) ? "Signed in" : $"Signed in as {name}";

        // Kick off the initial loads without blocking construction.
        _ = Records.LoadCommand.ExecuteAsync(null);
        _ = Chat.LoadHistoryCommand.ExecuteAsync(null);
        _ = Recommendations.LoadCommand.ExecuteAsync(null);
    }

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            await _api.LogoutAsync();
        }
        catch
        {
            // Logout is best-effort; the session is cleared regardless.
        }

        _onSignedOut();
    }
}

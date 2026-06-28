// Author: SA62 Group 4 - Root navigation between login and the signed-in shell (REQ-21).
using CommunityToolkit.Mvvm.ComponentModel;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class MainWindowViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private readonly SessionStore _session;

    [ObservableProperty]
    private ViewModelBase _currentPage;

    public MainWindowViewModel(IApiClient api, SessionStore session)
    {
        _api = api;
        _session = session;
        _currentPage = new LoginViewModel(api, OnAuthenticated);
    }

    private void OnAuthenticated()
    {
        CurrentPage = new ShellViewModel(_api, _session, OnSignedOut);
    }

    private void OnSignedOut()
    {
        CurrentPage = new LoginViewModel(_api, OnAuthenticated);
    }
}

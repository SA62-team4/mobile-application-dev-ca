// Author: SA62 Group 4 - Login and registration screen logic (REQ-02, REQ-21, NFR-02).
using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessDesktop.Models;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class LoginViewModel : ViewModelBase
{
    private readonly IApiClient _api;
    private readonly Action _onAuthenticated;

    [ObservableProperty] private string _email = string.Empty;
    [ObservableProperty] private string _password = string.Empty;
    [ObservableProperty] private string _displayName = string.Empty;
    [ObservableProperty] private bool _isRegisterMode;
    [ObservableProperty] private bool _isBusy;
    [ObservableProperty] private string? _errorMessage;

    public LoginViewModel(IApiClient api, Action onAuthenticated)
    {
        _api = api;
        _onAuthenticated = onAuthenticated;
    }

    public string SubmitLabel => IsRegisterMode ? "Create account" : "Log in";
    public string ToggleLabel => IsRegisterMode ? "Have an account? Log in" : "New here? Create an account";

    partial void OnIsRegisterModeChanged(bool value)
    {
        OnPropertyChanged(nameof(SubmitLabel));
        OnPropertyChanged(nameof(ToggleLabel));
    }

    [RelayCommand]
    private void ToggleMode()
    {
        ErrorMessage = null;
        IsRegisterMode = !IsRegisterMode;
    }

    [RelayCommand]
    private async Task SubmitAsync()
    {
        ErrorMessage = null;

        if (string.IsNullOrWhiteSpace(Email) || string.IsNullOrWhiteSpace(Password)
            || (IsRegisterMode && string.IsNullOrWhiteSpace(DisplayName)))
        {
            ErrorMessage = "Please fill in all required fields.";
            return;
        }

        IsBusy = true;
        try
        {
            if (IsRegisterMode)
            {
                await _api.RegisterAsync(new RegisterRequest
                {
                    DisplayName = DisplayName.Trim(),
                    Email = Email.Trim(),
                    Password = Password
                });
            }

            await _api.LoginAsync(new LoginRequest { Email = Email.Trim(), Password = Password });
            _onAuthenticated();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not reach the server. Please check the backend is running.";
        }
        finally
        {
            IsBusy = false;
        }
    }
}

// @author Tiong Zhong Cheng
using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessDesktop.Models;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class ChatViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<ChatMessageDto> Messages { get; } = new();

    [ObservableProperty] private string _question = string.Empty;
    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _isEmpty;
    [ObservableProperty] private string? _errorMessage;

    public ChatViewModel(IApiClient api)
    {
        _api = api;
    }

    [RelayCommand]
    private async Task LoadHistoryAsync()
    {
        IsLoading = true;
        ErrorMessage = null;
        try
        {
            var history = await _api.GetChatHistoryAsync();
            Messages.Clear();
            foreach (var message in history)
            {
                Messages.Add(message);
            }

            IsEmpty = Messages.Count == 0;
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not load chat history.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private async Task AskAsync()
    {
        if (string.IsNullOrWhiteSpace(Question))
        {
            return;
        }

        IsLoading = true;
        ErrorMessage = null;
        try
        {
            var answer = await _api.AskChatAsync(Question.Trim());
            Messages.Insert(0, answer);
            IsEmpty = false;
            Question = string.Empty;
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not get an answer. Please try again.";
        }
        finally
        {
            IsLoading = false;
        }
    }
}

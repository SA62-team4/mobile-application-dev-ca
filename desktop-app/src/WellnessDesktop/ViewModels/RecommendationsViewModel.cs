// Author: SA62 Group 4 - AI recommendations screen logic (REQ-13, REQ-21, NFR-02, NFR-04).
using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessDesktop.Models;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class RecommendationsViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<RecommendationDto> Recommendations { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _isGenerating;
    [ObservableProperty] private bool _isEmpty;
    [ObservableProperty] private string? _errorMessage;

    public RecommendationsViewModel(IApiClient api)
    {
        _api = api;
    }

    [RelayCommand]
    private async Task LoadAsync()
    {
        IsLoading = true;
        ErrorMessage = null;
        try
        {
            var items = await _api.GetRecommendationsAsync();
            Recommendations.Clear();
            foreach (var item in items)
            {
                Recommendations.Add(item);
            }

            IsEmpty = Recommendations.Count == 0;
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not load recommendations.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private async Task GenerateAsync()
    {
        IsGenerating = true;
        ErrorMessage = null;
        try
        {
            var recommendation = await _api.GenerateRecommendationAsync();
            Recommendations.Insert(0, recommendation);
            IsEmpty = false;
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not generate a recommendation. Please try again.";
        }
        finally
        {
            IsGenerating = false;
        }
    }
}

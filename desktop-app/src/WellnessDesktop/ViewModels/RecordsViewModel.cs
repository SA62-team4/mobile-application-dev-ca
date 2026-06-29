// Author: SA62 Group 4 - Wellness record CRUD logic (REQ-04..REQ-07, REQ-21, NFR-02, NFR-04).
using System;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WellnessDesktop.Models;
using WellnessDesktop.Services;

namespace WellnessDesktop.ViewModels;

public partial class RecordsViewModel : ViewModelBase
{
    private readonly IApiClient _api;

    public ObservableCollection<WellnessRecordDto> Records { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private bool _isEmpty;
    [ObservableProperty] private string? _errorMessage;

    // Editor state (shared add/edit panel).
    [ObservableProperty] private bool _isEditorOpen;
    [ObservableProperty] private string _editorTitle = "Add record";
    [ObservableProperty] private string? _editorError;
    private long? _editId;

    [ObservableProperty] private string _editRecordDate = DateTime.Today.ToString("yyyy-MM-dd");
    [ObservableProperty] private string _editSleepHours = "7.5";
    [ObservableProperty] private string _editExerciseType = string.Empty;
    [ObservableProperty] private string _editExerciseMinutes = "30";
    [ObservableProperty] private string _editMoodScore = "4";
    [ObservableProperty] private string _editNotes = string.Empty;

    public RecordsViewModel(IApiClient api)
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
            var records = await _api.GetRecordsAsync();
            Records.Clear();
            foreach (var record in records)
            {
                Records.Add(record);
            }

            IsEmpty = Records.Count == 0;
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not load records. Please check the backend is running.";
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private void New()
    {
        _editId = null;
        EditorTitle = "Add record";
        EditorError = null;
        EditRecordDate = DateTime.Today.ToString("yyyy-MM-dd");
        EditSleepHours = "7.5";
        EditExerciseType = string.Empty;
        EditExerciseMinutes = "30";
        EditMoodScore = "4";
        EditNotes = string.Empty;
        IsEditorOpen = true;
    }

    [RelayCommand]
    private void Edit(WellnessRecordDto? record)
    {
        if (record is null)
        {
            return;
        }

        _editId = record.Id;
        EditorTitle = "Edit record";
        EditorError = null;
        EditRecordDate = record.RecordDate;
        EditSleepHours = record.SleepHours.ToString(CultureInfo.InvariantCulture);
        EditExerciseType = record.ExerciseType;
        EditExerciseMinutes = record.ExerciseMinutes.ToString(CultureInfo.InvariantCulture);
        EditMoodScore = record.MoodScore.ToString(CultureInfo.InvariantCulture);
        EditNotes = record.Notes ?? string.Empty;
        IsEditorOpen = true;
    }

    [RelayCommand]
    private void CancelEdit()
    {
        IsEditorOpen = false;
        EditorError = null;
    }

    [RelayCommand]
    private async Task SaveAsync()
    {
        EditorError = null;

        if (!TryBuildRequest(out var request, out var validationError))
        {
            EditorError = validationError;
            return;
        }

        try
        {
            if (_editId is long id)
            {
                await _api.UpdateRecordAsync(id, request);
            }
            else
            {
                await _api.CreateRecordAsync(request);
            }

            IsEditorOpen = false;
            await LoadAsync();
        }
        catch (ApiException ex)
        {
            EditorError = ex.Message;
        }
        catch (Exception)
        {
            EditorError = "Could not save the record. Please try again.";
        }
    }

    [RelayCommand]
    private async Task DeleteAsync(WellnessRecordDto? record)
    {
        if (record is null)
        {
            return;
        }

        try
        {
            await _api.DeleteRecordAsync(record.Id);
            await LoadAsync();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception)
        {
            ErrorMessage = "Could not delete the record. Please try again.";
        }
    }

    private bool TryBuildRequest(out WellnessRecordRequest request, out string? error)
    {
        request = new WellnessRecordRequest();
        error = null;

        if (string.IsNullOrWhiteSpace(EditRecordDate))
        {
            error = "Record date is required (yyyy-MM-dd).";
            return false;
        }

        if (!double.TryParse(EditSleepHours, NumberStyles.Float, CultureInfo.InvariantCulture, out var sleep))
        {
            error = "Sleep hours must be a number.";
            return false;
        }

        if (!int.TryParse(EditExerciseMinutes, NumberStyles.Integer, CultureInfo.InvariantCulture, out var minutes))
        {
            error = "Exercise minutes must be a whole number.";
            return false;
        }

        if (!int.TryParse(EditMoodScore, NumberStyles.Integer, CultureInfo.InvariantCulture, out var mood)
            || mood < 1 || mood > 5)
        {
            error = "Mood score must be between 1 and 5.";
            return false;
        }

        request.RecordDate = EditRecordDate.Trim();
        request.SleepHours = sleep;
        request.ExerciseType = EditExerciseType.Trim();
        request.ExerciseMinutes = minutes;
        request.MoodScore = mood;
        request.Notes = string.IsNullOrWhiteSpace(EditNotes) ? null : EditNotes.Trim();
        return true;
    }
}

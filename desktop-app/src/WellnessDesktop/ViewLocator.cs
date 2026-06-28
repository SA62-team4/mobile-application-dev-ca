// Author: SA62 Group 4 - Maps a ViewModel to its View by naming convention (REQ-21).
using System;
using Avalonia.Controls;
using Avalonia.Controls.Templates;
using WellnessDesktop.ViewModels;

namespace WellnessDesktop;

public class ViewLocator : IDataTemplate
{
    public Control? Build(object? param)
    {
        if (param is null)
        {
            return null;
        }

        // Convention: ...ViewModels.FooViewModel -> ...Views.FooView
        var name = param.GetType().FullName!
            .Replace("ViewModels", "Views", StringComparison.Ordinal)
            .Replace("ViewModel", "View", StringComparison.Ordinal);

        var type = Type.GetType(name);
        if (type != null)
        {
            return (Control)Activator.CreateInstance(type)!;
        }

        return new TextBlock { Text = "Not Found: " + name };
    }

    public bool Match(object? data) => data is ViewModelBase;
}

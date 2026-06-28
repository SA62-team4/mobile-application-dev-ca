// Author: SA62 Group 4 - Application bootstrap and dependency wiring (REQ-21).
using System.Net.Http;
using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using WellnessDesktop.Services;
using WellnessDesktop.ViewModels;
using WellnessDesktop.Views;

namespace WellnessDesktop;

public partial class App : Application
{
    public override void Initialize() => AvaloniaXamlLoader.Load(this);

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            // Compose the small object graph by hand to avoid a DI container for this bonus client.
            var config = AppConfig.Load();
            var http = new HttpClient { BaseAddress = config.BackendBaseUri };
            var session = new SessionStore();
            var api = new ApiClient(http, session);

            desktop.MainWindow = new MainWindow
            {
                DataContext = new MainWindowViewModel(api, session)
            };
        }

        base.OnFrameworkInitializationCompleted();
    }
}

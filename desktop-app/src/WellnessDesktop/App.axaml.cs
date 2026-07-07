// @author Tiong Zhong Cheng
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
            // Manual wiring is enough for this small client.
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

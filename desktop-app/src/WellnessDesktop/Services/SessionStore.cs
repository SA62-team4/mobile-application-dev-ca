// Author: SA62 Group 4 - In-memory JWT/session holder (REQ-21, NFR-01).
using WellnessDesktop.Models;

namespace WellnessDesktop.Services;

/// <summary>
/// Holds the authenticated user's JWT and profile in memory only.
/// The token is never written to disk and is cleared on logout.
/// </summary>
public sealed class SessionStore
{
    public string? Token { get; private set; }

    public UserDto? CurrentUser { get; private set; }

    public bool IsAuthenticated => !string.IsNullOrEmpty(Token);

    public void SignIn(string token, UserDto? user)
    {
        Token = token;
        CurrentUser = user;
    }

    public void SignOut()
    {
        Token = null;
        CurrentUser = null;
    }
}

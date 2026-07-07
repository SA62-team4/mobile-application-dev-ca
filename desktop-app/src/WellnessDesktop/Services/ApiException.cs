// @author Tiong Zhong Cheng
using System;

namespace WellnessDesktop.Services;

/// <summary>
/// Thrown when the backend returns a non-success status. The message is already
/// user-friendly (taken from the standard error response) so the UI can show it
/// directly without exposing stack traces.
/// </summary>
public sealed class ApiException : Exception
{
    public int StatusCode { get; }

    public ApiException(string message, int statusCode = 0) : base(message)
    {
        StatusCode = statusCode;
    }
}

// Author: SA62 Group 4 - Test double that captures requests and returns canned responses.
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace WellnessDesktop.Tests;

internal sealed class StubHttpMessageHandler : HttpMessageHandler
{
    private readonly HttpStatusCode _status;
    private readonly string _responseJson;

    public List<HttpRequestMessage> Requests { get; } = new();

    public StubHttpMessageHandler(HttpStatusCode status, string responseJson)
    {
        _status = status;
        _responseJson = responseJson;
    }

    protected override Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Requests.Add(request);
        var response = new HttpResponseMessage(_status)
        {
            Content = new StringContent(_responseJson, System.Text.Encoding.UTF8, "application/json")
        };
        return Task.FromResult(response);
    }
}

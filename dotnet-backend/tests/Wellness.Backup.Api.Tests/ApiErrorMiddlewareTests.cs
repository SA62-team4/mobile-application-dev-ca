using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Wellness.Backup.Api.Errors;
using Wellness.Backup.Api.Middleware;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the error middleware maps ApiException, bad request, and unexpected
/// exceptions to the Spring-compatible JSON error shape, and leaves a started
/// response untouched. Uses an in-memory HttpContext, so no server is required.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class ApiErrorMiddlewareTests
{
    private static async Task<(int Status, ApiErrorResponse? Body)> Run(RequestDelegate next, string path = "/api/x")
    {
        var context = new DefaultHttpContext();
        context.Request.Path = path;
        var body = new MemoryStream();
        context.Response.Body = body;

        var middleware = new ApiErrorMiddleware(next, NullLogger<ApiErrorMiddleware>.Instance);
        await middleware.InvokeAsync(context);

        body.Position = 0;
        var text = Encoding.UTF8.GetString(body.ToArray());
        var parsed = string.IsNullOrEmpty(text)
            ? null
            : JsonSerializer.Deserialize<ApiErrorResponse>(text, new JsonSerializerOptions(JsonSerializerDefaults.Web));
        return (context.Response.StatusCode, parsed);
    }

    [Fact]
    public async Task ApiException_IsWrittenWithItsStatusAndMessage()
    {
        var (status, body) = await Run(_ => throw ApiException.NotFound("missing"));

        Assert.Equal(StatusCodes.Status404NotFound, status);
        Assert.NotNull(body);
        Assert.Equal(404, body!.Status);
        Assert.Equal("Not Found", body.Error);
        Assert.Equal("missing", body.Message);
        Assert.Equal("/api/x", body.Path);
    }

    [Fact]
    public async Task BadHttpRequest_MapsToBadRequest()
    {
        var (status, body) = await Run(_ => throw new BadHttpRequestException("bad body"));

        Assert.Equal(StatusCodes.Status400BadRequest, status);
        Assert.Equal("Invalid request body", body!.Message);
    }

    [Fact]
    public async Task UnexpectedException_MapsToInternalServerError()
    {
        var (status, body) = await Run(_ => throw new InvalidOperationException("boom"));

        Assert.Equal(StatusCodes.Status500InternalServerError, status);
        Assert.Equal("Internal Server Error", body!.Error);
        Assert.Equal("Unexpected server error", body.Message);
    }

    [Fact]
    public async Task SuccessfulRequest_PassesThroughUntouched()
    {
        var (status, body) = await Run(context =>
        {
            context.Response.StatusCode = StatusCodes.Status204NoContent;
            return Task.CompletedTask;
        });

        Assert.Equal(StatusCodes.Status204NoContent, status);
        Assert.Null(body);
    }
}

using Microsoft.AspNetCore.Http;
using Wellness.Backup.Api.Errors;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Locks each <see cref="ApiException"/> factory to its HTTP status code and checks
/// the Spring-compatible <see cref="ApiErrorResponse"/> payload shape.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class ApiErrorTests
{
    [Fact]
    public void Factories_MapToExpectedStatusCodes()
    {
        Assert.Equal(StatusCodes.Status400BadRequest, ApiException.BadRequest("m").StatusCode);
        Assert.Equal(StatusCodes.Status401Unauthorized, ApiException.Unauthorized("m").StatusCode);
        Assert.Equal(StatusCodes.Status403Forbidden, ApiException.Forbidden("m").StatusCode);
        Assert.Equal(StatusCodes.Status404NotFound, ApiException.NotFound("m").StatusCode);
        Assert.Equal(StatusCodes.Status409Conflict, ApiException.Conflict("m").StatusCode);
        Assert.Equal(StatusCodes.Status503ServiceUnavailable, ApiException.ServiceUnavailable("m").StatusCode);
    }

    [Fact]
    public void Factory_PreservesMessage()
    {
        var exception = ApiException.NotFound("missing");
        Assert.Equal("missing", exception.Message);
    }

    [Fact]
    public void ApiErrorResponse_RetainsFields()
    {
        var now = DateTimeOffset.UtcNow;
        var response = new ApiErrorResponse(now, 404, "Not Found", "missing", "/api/thing");

        Assert.Equal(now, response.Timestamp);
        Assert.Equal(404, response.Status);
        Assert.Equal("Not Found", response.Error);
        Assert.Equal("missing", response.Message);
        Assert.Equal("/api/thing", response.Path);
    }
}

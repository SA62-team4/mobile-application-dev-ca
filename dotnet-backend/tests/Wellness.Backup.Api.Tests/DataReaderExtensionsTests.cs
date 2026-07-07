using System.Data;
using Wellness.Backup.Api.Data;
using Xunit;

namespace Wellness.Backup.Api.Tests;

/// <summary>
/// Verifies the typed MySQL reader conversions, including DBNull handling and the
/// UTC-kind normalisation, using an in-memory <see cref="DataTableReader"/>.
/// </summary>
/// <remarks>@author Tiong Zhong Cheng</remarks>
public sealed class DataReaderExtensionsTests
{
    private static DataTableReader SingleRowReader()
    {
        var table = new DataTable();
        table.Columns.Add("name", typeof(string));
        table.Columns.Add("opt", typeof(string));
        table.Columns.Add("big", typeof(long));
        table.Columns.Add("num", typeof(int));
        table.Columns.Add("dec", typeof(decimal));
        table.Columns.Add("flag", typeof(bool));
        table.Columns.Add("day", typeof(DateTime));
        table.Columns.Add("ts", typeof(DateTime));

        table.Rows.Add("Ada", DBNull.Value, 123456789012L, 42, 7.5m, true,
            new DateTime(2026, 1, 15), new DateTime(2026, 1, 15, 8, 30, 0, DateTimeKind.Unspecified));

        var reader = table.CreateDataReader();
        Assert.True(reader.Read());
        return reader;
    }

    [Fact]
    public void GetRequiredString_ReturnsValue()
    {
        using var reader = SingleRowReader();
        Assert.Equal("Ada", reader.GetRequiredString("name"));
    }

    [Fact]
    public void GetOptionalString_ReturnsNullOnDbNull()
    {
        using var reader = SingleRowReader();
        Assert.Null(reader.GetOptionalString("opt"));
        Assert.Equal("Ada", reader.GetOptionalString("name"));
    }

    [Fact]
    public void NumericAccessors_ReturnTypedValues()
    {
        using var reader = SingleRowReader();
        Assert.Equal(123456789012L, reader.GetInt64Value("big"));
        Assert.Equal(42, reader.GetInt32Value("num"));
        Assert.Equal(7.5m, reader.GetDecimalValue("dec"));
        Assert.True(reader.GetBooleanValue("flag"));
    }

    [Fact]
    public void GetDateOnlyValue_ReturnsDatePart()
    {
        using var reader = SingleRowReader();
        Assert.Equal(new DateOnly(2026, 1, 15), reader.GetDateOnlyValue("day"));
    }

    [Fact]
    public void GetUtcDateTime_ForcesUtcKind()
    {
        using var reader = SingleRowReader();
        var value = reader.GetUtcDateTime("ts");

        Assert.Equal(DateTimeKind.Utc, value.Kind);
        Assert.Equal(new DateTime(2026, 1, 15, 8, 30, 0), value);
    }
}

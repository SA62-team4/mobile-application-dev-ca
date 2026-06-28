using System.Data.Common;

namespace Wellness.Backup.Api.Data;

/// <summary>
/// Small data-reader helpers for stable MySQL type conversion.
/// </summary>
/// <remarks>@author SA62 Team</remarks>
public static class DataReaderExtensions
{
    public static string GetRequiredString(this DbDataReader reader, string name)
    {
        return reader.GetString(reader.GetOrdinal(name));
    }

    public static string? GetOptionalString(this DbDataReader reader, string name)
    {
        var ordinal = reader.GetOrdinal(name);
        return reader.IsDBNull(ordinal) ? null : reader.GetString(ordinal);
    }

    public static long GetInt64Value(this DbDataReader reader, string name)
    {
        return reader.GetInt64(reader.GetOrdinal(name));
    }

    public static int GetInt32Value(this DbDataReader reader, string name)
    {
        return reader.GetInt32(reader.GetOrdinal(name));
    }

    public static decimal GetDecimalValue(this DbDataReader reader, string name)
    {
        return reader.GetDecimal(reader.GetOrdinal(name));
    }

    public static bool GetBooleanValue(this DbDataReader reader, string name)
    {
        return Convert.ToBoolean(reader[name]);
    }

    public static DateOnly GetDateOnlyValue(this DbDataReader reader, string name)
    {
        return DateOnly.FromDateTime(reader.GetDateTime(reader.GetOrdinal(name)));
    }

    public static DateTime GetUtcDateTime(this DbDataReader reader, string name)
    {
        var value = reader.GetDateTime(reader.GetOrdinal(name));
        return value.Kind == DateTimeKind.Utc ? value : DateTime.SpecifyKind(value, DateTimeKind.Utc);
    }
}

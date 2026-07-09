using System.Text.Json;
using Wellness.Backup.Api.Configuration;
using Wellness.Backup.Api.Data;
using Wellness.Backup.Api.Endpoints;
using Wellness.Backup.Api.Middleware;
using Wellness.Backup.Api.Repositories;
using Wellness.Backup.Api.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    options.SerializerOptions.DictionaryKeyPolicy = JsonNamingPolicy.CamelCase;
});

builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod());
});

builder.Services.AddSingleton(BackendOptions.FromConfiguration(builder.Configuration));
builder.Services.AddSingleton<MySqlConnectionFactory>();
builder.Services.AddSingleton<DatabaseSchemaInitializer>();
builder.Services.AddSingleton<UserRepository>();
builder.Services.AddSingleton<WellnessRecordRepository>();
builder.Services.AddSingleton<ChatMessageRepository>();
builder.Services.AddSingleton<RecommendationRepository>();
builder.Services.AddSingleton<JwtTokenService>();
builder.Services.AddSingleton<PasswordService>();
// In-memory login throttling (S-04). Tunable via config; defaults mirror Spring
// (5 attempts allowed, lockout for 180s once exceeded).
builder.Services.AddSingleton(sp =>
{
    var config = sp.GetRequiredService<IConfiguration>();
    var maxAttempts = config.GetValue("Security:Login:MaxAttempts", 5);
    var lockoutSeconds = config.GetValue("Security:Login:LockoutSeconds", 180);
    return new LoginAttemptService(maxAttempts, TimeSpan.FromSeconds(lockoutSeconds));
});
builder.Services.AddHttpClient<AiServiceClient>();
builder.Services.AddHttpClient<GoogleTokenVerifier>();

var app = builder.Build();

app.UseMiddleware<ApiErrorMiddleware>();
app.UseCors();

await app.Services.GetRequiredService<DatabaseSchemaInitializer>().InitializeAsync();

app.MapStatusEndpoints();
app.MapAuthEndpoints();
app.MapWellnessRecordEndpoints();
app.MapChatEndpoints();
app.MapRecommendationEndpoints();
app.MapAccountEndpoints();
app.MapInternalEndpoints();
app.MapMethods("/{*path}", ["OPTIONS"], (string path) => Results.NoContent());

app.Run();

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY Wellness.Backup.Api.csproj ./
RUN dotnet restore Wellness.Backup.Api.csproj

COPY . ./
RUN dotnet publish Wellness.Backup.Api.csproj -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
COPY --from=build /app/publish ./
ENTRYPOINT ["dotnet", "Wellness.Backup.Api.dll"]

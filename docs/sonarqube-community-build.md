# SonarQube Community Build Setup

## Purpose

SonarQube Community Build provides the shared code-quality dashboard for the
SA62 wellness monorepo. It is evidence for `REQ-16` and the validation gates,
but it is not part of the core Android/Spring/Python/Ollama demo path.

## Deployment Model

Run SonarQube on a separate DigitalOcean Droplet or local quality host:

```text
sa62wellness-sonar.duckdns.org
  Caddy HTTPS reverse proxy
  SonarQube Community Build
  PostgreSQL
```

Current team dashboard:

```text
https://sa62wellness-sonar.duckdns.org
```

Recommended DigitalOcean size:

```text
4 vCPU / 8 GB RAM
```

Minimum workable size:

```text
2 vCPU / 4 GB RAM
```

Only expose ports `22`, `80`, and `443` publicly. Do not expose SonarQube's
internal `9000` port to the internet.

## Start The Server

### Option A: GitHub Actions Terraform + Ansible

Provision the droplet with:

```text
Actions -> Infra (Terraform) -> action plan
Actions -> Infra (Terraform) -> action apply
```

The workflow uses the combined `infra/terraform/` root and the same
DigitalOcean/Spaces secrets as the app infrastructure. After apply, copy the
`sonar_reserved_ip` output into the GitHub Environment secret:

```text
SONAR_DROPLET_HOST=<reserved_ip>
```

### GitHub Setup Checklist

Your current `production` GitHub Environment already has most of the shared
DigitalOcean/app deployment values. Keep these as-is; SonarQube reuses them:

```text
Shared production Environment secrets:
DIGITALOCEAN_TOKEN
SPACES_ACCESS_KEY
SPACES_SECRET_KEY
DEPLOY_SSH_KEY

Shared production Environment variables:
TF_STATE_BUCKET=sa62-wellness-tfstate
TF_STATE_ENDPOINT=https://sgp1.digitaloceanspaces.com
DO_REGION=sgp1
DROPLET_SIZE=s-4vcpu-8gb
SSH_KEY_NAME=mobile-ca
MANAGE_DNS=false
API_DOMAIN=sa62wellness.duckdns.org
GOOGLE_CLIENT_ID=<existing Google web client id>
```

With your current DuckDNS setup, `MANAGE_DNS=false`, so `DOMAIN` and
`SUBDOMAIN` are not used by Terraform DNS creation. `API_DOMAIN` remains the
wellness app hostname only.

Add these SonarQube-only production Environment variables:

```text
SONAR_DROPLET_SIZE=s-2vcpu-4gb
SONAR_DOMAIN=sa62wellness-sonar.duckdns.org
SONAR_POSTGRES_DB=sonarqube
SONAR_POSTGRES_USER=sonarqube
```

Optional SonarQube-only production Environment variable:

```text
SONAR_SUBDOMAIN=sonar
```

`SONAR_SUBDOMAIN` is only useful when `MANAGE_DNS=true`. With
`MANAGE_DNS=false`, create a separate DuckDNS hostname for SonarQube, point it
to `sonar_reserved_ip`, and set `SONAR_DOMAIN` to that full hostname.

Add these SonarQube-only production Environment secrets:

```text
SONAR_POSTGRES_PASSWORD=<strong random password>
SONAR_DROPLET_HOST=<sonar_reserved_ip after terraform apply>
```

Useful password commands:

```bash
openssl rand -base64 24
```

Then deploy SonarQube with:

```text
Actions -> Deploy -> target sonar
```

After SonarQube is running and the CI analysis token has been created, add these
at **repository level** because `ci.yml` does not use the protected
`production` Environment:

```text
Repository variable:
SONAR_HOST_URL=https://sa62wellness-sonar.duckdns.org

Repository secret:
SONAR_TOKEN=<SonarQube CI analysis token>
```

### Option B: Manual Compose

Create a private env file from the example:

```bash
cp .env.sonar.example .env.sonar
```

Set:

```text
SONAR_DOMAIN=sa62wellness-sonar.duckdns.org
SONAR_POSTGRES_PASSWORD=<strong random password>
```

Start the stack:

```bash
docker compose --env-file .env.sonar -f docker-compose.sonar.yml up -d
```

Open:

```text
https://sa62wellness-sonar.duckdns.org
```

The first login uses SonarQube's initial administrator flow. Change the default
administrator password immediately.

## Team Access

Create a group:

```text
sa62-team
```

Grant the group:

```text
Browse project
See source code
View issues
```

Create one user per team member or configure GitHub authentication for
account-based sign-in. Do not share the administrator account for normal review
work.

## GitHub Authentication

SonarSource's Community Build documentation recommends registering SonarQube as
a **GitHub App** for GitHub authentication and provisioning. OAuth Apps are
deprecated for this use and should not be used for the final team setup.

Reference:

```text
https://docs.sonarsource.com/sonarqube-community-build/instance-administration/authentication/github
```

Before creating the GitHub App, set the SonarQube server base URL:

```text
Administration -> Configuration -> General -> General
Server base URL = https://sa62wellness-sonar.duckdns.org
```

Create the GitHub App under the team/org owner that should authenticate users.
Use:

```text
GitHub App name:
SA62 Wellness SonarQube

Homepage URL:
https://sa62wellness-sonar.duckdns.org

Callback URL:
https://sa62wellness-sonar.duckdns.org

Webhooks:
Disabled
```

Set the minimum permissions needed for team sign-in:

```text
Organization permissions -> Members: Read-only
Account permissions -> Email addresses: Read-only
```

If automatic provisioning of organization/repository permissions is enabled,
also add the read-only administration permissions described in the SonarSource
GitHub authentication documentation. Install the GitHub App on the `SA62-team4`
organization.

In SonarQube:

```text
Administration
-> Configuration
-> General Settings
-> Authentication
-> GitHub
-> Create configuration
```

Enter the values from the GitHub App:

```text
Client ID
Client Secret
GitHub App ID
Private Key
API URL = https://api.github.com
WEB URL = https://github.com
Organizations = SA62-team4
```

Enable the configuration, enable just-in-time provisioning if team members
should be created on first login, then click **Test configuration**. Keep the CI
analysis token separate from GitHub login; GitHub Actions still uses the
repository-level `SONAR_TOKEN` secret.

## CI Token

After the dashboard is reachable, create the CI token in SonarQube:

1. Log in as an administrator.
2. Create these projects, or let the first CI scan create them if your token has
   enough permission:

```text
sa62-wellness-spring-backend
sa62-wellness-android
sa62-wellness-python-ai
sa62-wellness-dotnet-backend
```

3. Create a dedicated CI user or service account.
4. Grant it execute-analysis permission for the projects.
5. Generate a token and store it in GitHub:

```text
Repository variable:
SONAR_HOST_URL=https://sa62wellness-sonar.duckdns.org

Repository secret:
SONAR_TOKEN=<ci analysis token>
```

The CI scan steps are guarded, so they are skipped when these values are absent
or when a pull request comes from a fork that cannot receive secrets.

## CI Projects

The CI workflow scans the main implemented components separately:

```text
spring-backend      Maven Sonar scanner
android-app         SonarQube scan action after Gradle build/test/lint
python-ai-service   SonarQube scan action after Python compile check
dotnet-backend      SonarScanner for .NET around dotnet test
```

## Marking Evidence

Before submission, capture:

```text
SonarQube dashboard URL
Quality gate status
Issue counts by component
CI run showing Sonar scans completed
Known limitations and accepted issues
```

SonarQube does not replace:

```text
mvn test
Gradle test/lint
pytest or Python smoke checks
Gitleaks/Trivy/OSV dependency and secret scans
Codex Security scans for security-sensitive changes
```

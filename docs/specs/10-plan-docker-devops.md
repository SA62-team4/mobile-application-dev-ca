# 08 Docker And DevOps Spec

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-14, REQ-16, NFR-03, NFR-05 |
| Primary audience | Integration owner, backend owner, Python AI owner |
| Upstream specs | `04-plan-system-architecture.md`, `06-plan-api-contracts.md`, `08-plan-rag-ai-design.md` |
| Downstream specs | Docker Compose, GitHub Actions, Ansible playbooks (`infra/ansible/`), setup docs |

## Goal

Dockerise as much of the backend/runtime stack as practical while keeping Android development in Android Studio. Use GitHub Actions to keep the team integration branch healthy.

## Docker Scope

Dockerise:

- MySQL
- Spring Boot backend
- Optional `.NET Backup API` cold-standby backend
- Python FastAPI AI service
- Ollama
- Chroma/vector persistence, either embedded in Python process with a persistent volume or separate service if chosen later
- Optional Adminer or phpMyAdmin

Do not Dockerise:

- Android app runtime or emulator

Quality tooling:

- SonarQube Community Build may be Dockerised as a separate quality dashboard
  stack using `docker-compose.sonar.yml`. It must not be part of the default
  runtime/demo command because it adds PostgreSQL, persistent quality-analysis
  state, administrator setup, and extra memory pressure that are unrelated to
  the wellness app flow.

## Planned Compose Services

| Service | Purpose | Notes |
| --- | --- | --- |
| `mysql` | Transactional database | Named volume for persistent data |
| `spring-backend` | REST API and business logic | Depends on MySQL and Python AI service |
| `dotnet-backend` | Optional cold-standby REST API mirror | Backup profile/service on host port `8082`; Spring remains canonical |
| `python-ai-service` | RAG and agentic AI | Depends on Ollama and vector volume |
| `ollama` | Local model runtime | Named volume for models |
| `adminer` | Optional DB inspection | Demo/debug convenience only |

## Planned Volumes

- `mysql-data`
- `ollama-data`
- `chroma-data`

## Environment Variables

Create `.env.example` during implementation with:

```text
MYSQL_DATABASE=wellness_app
MYSQL_USER=wellness_user
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_root
MYSQL_HOST_PORT=3307
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/wellness_app
SPRING_DATASOURCE_USERNAME=wellness_user
SPRING_DATASOURCE_PASSWORD=change_me
SPRING_HOST_PORT=8080
JWT_SECRET=replace_with_long_random_secret
JWT_EXPIRY_SECONDS=86400
GOOGLE_CLIENT_ID=
AI_SERVICE_URL=http://python-ai-service:8000
AI_SERVICE_HOST_PORT=8000
INTERNAL_SERVICE_TOKEN=replace_with_internal_token
DOTNET_BACKEND_HOST_PORT=8082
DOTNET_CONNECTION_STRING=Server=mysql;Port=3306;Database=wellness_app;User=wellness_user;Password=change_me;TreatTinyAsBoolean=true;
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_HOST_PORT=11434
OLLAMA_GENERATION_MODEL=llama3.2:3b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text:latest
CHROMA_PERSIST_DIR=/data/chroma
ADMINER_HOST_PORT=8081
```

Host-facing ports must be configurable so local tools such as Homebrew MySQL do not block Docker Compose. The default MySQL host port is `3307`, while container-to-container traffic continues to use `mysql:3306`.

## Optional .NET Backup Mode

The normal demo command must continue to run Spring Boot on host port `8080`:

```text
docker compose up --build
```

Backup rehearsal may use an additional Compose override:

```text
docker compose -f docker-compose.yml -f docker-compose.dotnet-backup.yml up --build dotnet-backend python-ai-service mysql ollama
```

Backup mode rules:

- `dotnet-backend` exposes the same API contract on `${DOTNET_BACKEND_HOST_PORT:-8082}`.
- The Python service uses `BACKEND_BASE_URL=http://dotnet-backend:8080` when backup mode is selected.
- Android can rehearse backup by switching its backend base URL to `http://10.0.2.2:8082/` on emulator or `http://127.0.0.1:8082/` with matching `adb reverse` on a physical device.
- Do not add an automatic gateway or failover service unless a later spec revision accepts the complexity.

## Local Setup Contract

Future setup commands should be documented as:

```text
docker compose up -d mysql ollama
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text:latest
docker compose up --build
```

Android developers should configure the backend base URL based on emulator/device networking:

- Android emulator to host machine: `http://10.0.2.2:<backend-port>/`
- Physical device primary demo path: USB debugging with `adb reverse tcp:<backend-port> tcp:<backend-port>` and Android URL `http://127.0.0.1:<backend-port>/`
- The Android build may override the default URL with `WELLNESS_API_BASE_URL` as a Gradle property or environment variable; local IP addresses must not be committed.

## GitHub Workflow

Recommended branches:

- `main`: protected and demo-ready.
- `develop`: integration branch.
- `feature/<area>-<short-description>`: feature work.

PR requirements:

- Summary of change.
- Tests run.
- Screenshots for Android UI changes.
- API request/response examples for backend changes.
- Codex Security diff scan evidence for security-sensitive changes, following `SECURITY.md`.
- Reviewer approval before merge.

## Codex Security Review

The repository-level `SECURITY.md` defines when to run Codex Security scans and what evidence must be attached to PRs. The Codex Security plugin is assumed to be installed in the developer environment; the project does not require adding paid scanner services or cloud-only security tooling.

Scan expectations:

- Use Codex Security diff scans for PRs that change auth, JWT, ownership checks, API trust boundaries, Docker/runtime configuration, secret handling, Android token storage, Python AI callbacks, or optional backup-client parity.
- Use Codex Security repository or scoped-path scans before major milestone demos, before final submission, and after large security-relevant rewrites.
- Treat high or critical valid findings as merge blockers unless the team records a clear suppression/deferment rationale that does not compromise demo data, secrets, or cross-user access.
- Record scan type, scope, date, finding summary, report location, fixes, and accepted suppressions in the PR or submission evidence.

## GitHub Actions

CI should run on pull requests to `develop` and `main`.

Jobs:

- Android build and unit tests.
- Spring Boot tests.
- Optional .NET backup backend tests when `dotnet-backend/` exists.
- Python tests.
- Docker image build smoke check.
- Compose smoke test for MySQL, Spring Boot, and Python AI service.
- Optional backup Compose override config check when `docker-compose.dotnet-backup.yml` exists.
- SonarQube Community Build scans for implemented components when
  `SONAR_HOST_URL` and `SONAR_TOKEN` are configured. CI must skip these scan
  steps for forked pull requests or unconfigured repositories so ordinary
  coursework builds remain reproducible.
- Spring Boot SonarQube scans must run after Maven `verify` so unit tests
  execute and `target/site/jacoco/jacoco.xml` is generated. The Maven project
  must configure JaCoCo XML reporting and pass that report path to SonarQube;
  otherwise dashboard coverage is expected to show `0%` even when tests exist.
- Supply chain lockfile verification: regenerates each committed lockfile and
  fails on drift so Semgrep Supply Chain (Managed Scans) can always resolve
  dependencies. See "Supply Chain Dependency Scanning" below.
- Manual Codex Security scan evidence is required in PR notes for security-sensitive changes; automated CI must not depend on a paid or cloud-only scanner.

Do not run heavyweight Ollama generation in CI by default. Use mocks or a lightweight health-check path so CI stays fast and reliable.

## SonarQube Community Build Quality Dashboard

SonarQube Community Build is the preferred free/self-managed dashboard for
marking evidence around code quality, maintainability, duplication, reliability,
and reviewed security issues. It supports `REQ-16` evidence but does not replace
unit tests, Android lint, Codex Security review, or dedicated secret/dependency
scanners.

Deployment model:

- Run SonarQube on a separate local host or DigitalOcean Droplet. The current
  team dashboard is `https://sa62wellness-sonar.duckdns.org`.
- Use the combined `infra/terraform/` root to provision both the app Droplet and
  the dedicated SonarQube Droplet, reserved IPs, firewalls, and optional DNS
  records.
- Use the combined `infra/ansible/site.yml` playbook. The `wellness` host runs
  the app roles, and the `sonar` host runs the SonarQube role that starts
  `docker-compose.sonar.yml` with SonarQube Community Build, PostgreSQL, and
  Caddy. Only ports `22`, `80`, and `443` should be public; SonarQube's
  internal `9000` port stays behind Caddy.
- Recommended Droplet size is 4 vCPU / 8 GB RAM. A 2 vCPU / 4 GB RAM Droplet is
  acceptable for light coursework use but may be slower during indexing.
- Team members must have individual SonarQube accounts or GitHub App-backed
  sign-in and read access to the dashboard. The CI token must be separate from
  human administrator accounts. SonarSource's Community Build documentation
  deprecates OAuth App authentication for this purpose and recommends GitHub App
  authentication/provisioning.

CI integration:

- GitHub Actions stores `SONAR_HOST_URL` as a repository variable and
  `SONAR_TOKEN` as a repository secret.
- `infra.yml` runs the combined `infra/terraform/` root and manages both the app
  and SonarQube infrastructure in one state.
- `deploy.yml` supports a `target` input. `target=app` runs `site.yml`;
  `target=sonar` runs `site.yml --limit sonar` against `SONAR_DROPLET_HOST`.
- `ci.yml` scans the implemented components as separate SonarQube projects:
  `sa62-wellness-spring-backend`, `sa62-wellness-android`,
  `sa62-wellness-python-ai`, and `sa62-wellness-dotnet-backend`.
- The optional `.NET Backup API` remains backup evidence only, but its CI job
  also publishes SonarQube dashboard evidence when the shared SonarQube
  repository variable and secret are configured.
- Community Build branch and PR support is limited; final evidence should be
  captured from the canonical `develop` or `main` branch scan.

Security and quality boundaries:

- SonarQube Community Build is not the project's SCA or secrets source of truth.
  Use Gitleaks, Trivy, OSV-Scanner, or Codex Security evidence where those risks
  matter.
- Valid high or critical security findings still follow `SECURITY.md`; a green
  SonarQube quality gate alone is not sufficient security evidence.

## Supply Chain Dependency Scanning (Semgrep Managed Scans)

Semgrep Supply Chain runs as a Managed Scan and does not build projects, so it
can only resolve dependencies from committed lockfiles/manifests. Each
non-Python ecosystem therefore commits a resolvable lockfile:

- Spring Boot (Maven): `spring-backend/maven_dep_tree.txt`, generated by
  `mvn dependency:tree -DoutputType=text -DoutputFile=maven_dep_tree.txt`.
- Android (Gradle): `android-app/app/gradle.lockfile`, produced by Gradle
  dependency locking (`dependencyLocking { lockAllConfigurations() }` in
  `app/build.gradle`) via `./gradlew :app:dependencies --write-locks`.
- .NET (NuGet): `packages.lock.json` per project, enabled by
  `RestorePackagesWithLockFile=true` in each solution's `Directory.Build.props`
  and produced by `dotnet restore`.
- Python (pip): `python-ai-service/requirements.txt` is already a pinned
  lockfile; no extra file is needed.

These lockfiles must be committed and kept in sync with their manifests. The
`ci.yml` `lockfiles` job regenerates them and fails on drift (`git diff` for
Maven/Gradle, `dotnet restore --locked-mode` for .NET), so a dependency change
without a matching lockfile update cannot reach `develop` or `main`. When you
change a dependency, regenerate the affected lockfile with the command above and
commit it alongside the manifest change.

## DigitalOcean Deployment (Production)

The chosen cloud target is a single DigitalOcean Droplet running the Compose
stack. Infrastructure is provisioned with Terraform; server configuration and
application deployment are handled by Ansible (`infra/ansible/`), invoked from
GitHub Actions; secrets live in GitHub Actions secrets.

Topology and sizing:

- One Ubuntu 24.04 Droplet. Default **8 GB / 4 vCPU** (`s-4vcpu-8gb`) with enforced
  `mem_limit`s in the prod overlay so the stack fits; **16 GB** (`g-4vcpu-16gb`)
  is roomier but restricted on new DO accounts (raise the tier via a ticket).
  Ollama runs on-server; CPU inference is slow but functional.
- Caddy is the only public service (80/443) and terminates TLS via automatic
  Let's Encrypt for `api.<domain>`, reverse-proxying `spring-backend:8080`.
- MySQL, Ollama, Python AI, and Spring Boot stay on the internal `wellness-net`;
  Adminer is disabled in production.
- The Python AI image runs as a non-root user and pre-creates `/data/chroma`
  with app-user ownership so the ChromaDB named volume can be written at startup.

Infrastructure (`infra/terraform/`):

- Resources: Droplet (cloud-init only creates the `deploy` user and installs
  Python so Ansible can take over), reserved IP, cloud firewall (inbound
  22/80/443 only), DNS A record (optional via `manage_dns`), project grouping.
- Remote state in a DO Space (S3-compatible); see `infra/terraform/README.md` for
  the recommended Space configuration.

Configuration and deployment (`infra/ansible/`):

- Ansible owns everything past provisioning. The `bootstrap` role installs
  Docker Engine + the compose plugin, adds `deploy` to the `docker` group, and
  ensures `/opt/wellness`. The `app` role logs in to GHCR, ships
  compose/Caddy/knowledge-base files, templates `.env` (mode `0600`) from
  secrets, pulls images and runs the prod overlay, ensures the Ollama models are
  present, prebuilds the Python RAG vector index, and verifies the HTTPS health
  endpoint. The playbook is idempotent: a second run reports no changes for
  unchanged config.
- Secrets are read from the environment via `lookup('env', ...)` (not CLI
  extra-vars) so they never appear in the process argv; non-sensitive config
  (image tag/prefix, API domain) is passed as extra-vars.

Deployment (`.github/workflows/`):

- `infra.yml` (manual) runs `terraform plan/apply/destroy`.
- `deploy.yml` builds `spring-backend` and `python-ai-service` images only when a
  push to `main` changes deploy-relevant paths (`spring-backend/`,
  `python-ai-service/`, `rag-knowledge-base/`, Compose/Caddy files, or
  `deploy.yml` itself), or when manually dispatched. It pushes the images to
  GHCR, then runs `ansible-playbook infra/ansible/site.yml` against the droplet
  to configure the host and deploy the stack.
- `ci.yml` runs `ansible-playbook --syntax-check` and `ansible-lint` on PRs so
  playbook regressions are caught without touching a real droplet.
- `deploy.yml`/`infra.yml` use a `production` GitHub Environment for an approval
  gate.

Secrets and variables (GitHub → Settings → Secrets and variables → Actions).
Store all secrets as **Environment secrets** on the `production` Environment (not
repo-wide), so PR workflows cannot read them and the approval gate applies.
Non-secret config goes in **Variables**.

| Name | Kind | Used by | How to obtain |
| --- | --- | --- | --- |
| `DIGITALOCEAN_TOKEN` | Secret (production) | Terraform provider; image registry | DO Control Panel → API → Tokens → Generate New Token (read+write scope) |
| `SPACES_ACCESS_KEY`, `SPACES_SECRET_KEY` | Secret (production) | Terraform remote state (DO Spaces) | DO → API → Spaces Keys → Generate New Key (secret shown once) |
| `DEPLOY_SSH_KEY` | Secret (production) | SSH into the Droplet | The **private** half of the SSH key you uploaded to DO (e.g. `cat ~/.ssh/wellness`) — paste the whole key |
| `GHCR_PAT` | Secret (production) | Droplet pulls private GHCR images | GitHub → Settings → Developer settings → Personal access tokens (classic) → scope `read:packages` |
| `DROPLET_HOST` | Secret (production) | Deploy target | `terraform output reserved_ip` after the infra apply (or the FQDN) |
| `JWT_SECRET` | Secret (production) | Rendered into Droplet `.env` | Generate: `openssl rand -hex 32` |
| `INTERNAL_SERVICE_TOKEN` | Secret (production) | Rendered into Droplet `.env` | Generate: `openssl rand -hex 24` |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `SPRING_DATASOURCE_PASSWORD` | Secret (production) | Rendered into Droplet `.env` | Pick strong values, e.g. `openssl rand -base64 24`; `MYSQL_PASSWORD` must equal `SPRING_DATASOURCE_PASSWORD` |
| `TF_STATE_BUCKET` | Variable | Terraform backend config | Name of the DO Space you created for state (e.g. `sa62-wellness-tfstate`) |
| `TF_STATE_ENDPOINT` | Variable | Terraform backend config | `https://<region>.digitaloceanspaces.com` for your Space's region |
| `DO_REGION` | Variable | Terraform tfvars | DO region slug, e.g. `sgp1` (`doctl compute region list`) |
| `DROPLET_SIZE` | Variable | Terraform tfvars | DO size slug, e.g. `s-4vcpu-8gb` for the Regular 4 vCPU / 8 GB plan (`doctl compute size list`) |
| `SSH_KEY_NAME` | Variable | Terraform tfvars | Name shown in DO → Settings → Security → SSH Keys |
| `MANAGE_DNS` | Variable | DNS toggle | `true` if your domain is in DO DNS, else `false` |
| `DOMAIN` | Variable | DNS + `.env` | Your registered domain (hosted in DO DNS when `MANAGE_DNS=true`) |
| `SUBDOMAIN` | Variable | DNS | Chosen API host label, e.g. `api` |
| `API_DOMAIN` | Variable | Caddy/`.env` | The full FQDN `SUBDOMAIN.DOMAIN`, e.g. `api.example.com` |
| `GOOGLE_CLIENT_ID` | Variable | Rendered into Droplet `.env`; backend Google ID token verification (REQ-22) | Google Cloud Console → APIs & Services → Credentials → the **Web** OAuth 2.0 client ID. Non-secret (also embedded in the Android APK). Leave unset to disable SSO in production. |

SonarQube quality dashboard deployment shares the same DigitalOcean, SSH, and
Terraform-state configuration as the wellness app Droplet:

```text
DIGITALOCEAN_TOKEN
SPACES_ACCESS_KEY
SPACES_SECRET_KEY
DEPLOY_SSH_KEY
TF_STATE_BUCKET
TF_STATE_ENDPOINT
DO_REGION
SSH_KEY_NAME
MANAGE_DNS
DROPLET_SIZE
```

`DROPLET_HOST`, `GHCR_PAT`, database passwords, `JWT_SECRET`,
`INTERNAL_SERVICE_TOKEN`, `API_DOMAIN`, and `GOOGLE_CLIENT_ID` remain app-only.
With the current DuckDNS setup (`MANAGE_DNS=false`), `DOMAIN`, `SUBDOMAIN`, and
`SONAR_SUBDOMAIN` are not used to create DNS records; create/update the DuckDNS
hostnames manually and store the final hostnames in `API_DOMAIN` and
`SONAR_DOMAIN`.

Add these SonarQube-specific values:

| Name | Kind | Used by | How to obtain |
| --- | --- | --- | --- |
| `SONAR_DROPLET_SIZE` | Variable | `infra.yml` | DO size slug, e.g. `s-2vcpu-4gb` minimum or `s-4vcpu-8gb` smoother indexing |
| `SONAR_SUBDOMAIN` | Variable | `infra.yml` | Optional when `MANAGE_DNS=false`; host label, normally `sonar`, only used for DO DNS records |
| `SONAR_DOMAIN` | Variable | `deploy.yml` with `target=sonar`, CI scans as `SONAR_HOST_URL=https://...` | Full SonarQube dashboard FQDN, currently `sa62wellness-sonar.duckdns.org` |
| `SONAR_POSTGRES_DB` | Variable | `deploy.yml` with `target=sonar` | Usually `sonarqube` |
| `SONAR_POSTGRES_USER` | Variable | `deploy.yml` with `target=sonar` | Usually `sonarqube` |
| `SONAR_DROPLET_HOST` | Secret (production) | Ansible SSH target for `deploy.yml` with `target=sonar` | `terraform output sonar_reserved_ip` from `infra/terraform` |
| `SONAR_POSTGRES_PASSWORD` | Secret (production) | Rendered into SonarQube `.env.sonar` | Generate: `openssl rand -base64 24` |
| `SONAR_TOKEN` | Secret (repository-level) | `ci.yml` SonarQube analysis scans | SonarQube user token with execute-analysis permission; repository-level because CI does not use the `production` Environment |
| `SONAR_HOST_URL` | Variable (repository-level) | `ci.yml` SonarQube analysis scans | `https://sa62wellness-sonar.duckdns.org`; repository-level because CI does not use the `production` Environment |

The built-in `GITHUB_TOKEN` (no setup) is used by `deploy.yml` to push images to
GHCR. Never store any of these in the repo, Terraform state, or cloud-init.

### Production schema fixes on the Droplet

`hibernate.ddl-auto: update` adds new columns but never relaxes an existing
constraint, so some schema changes must be applied by hand against the running
MySQL container. The prod overlay removes MySQL's host port, so connect through
Compose from `app_dir` (`/opt/wellness`) rather than from outside.

Enabling Google SSO (REQ-22) on a pre-existing database requires making
`password_hash` nullable (see `05-plan-backend-data-model-erd.md`). After `ssh`
into the Droplet:

```bash
cd /opt/wellness
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T mysql \
  sh -c 'mysql -uwellness_user -p"$MYSQL_PASSWORD" wellness_app \
    -e "ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL;"'
```

`sh -c '... "$MYSQL_PASSWORD" ...'` expands the password inside the container
(where it already exists in the env) so the secret never appears in shell
history or `ps`. Verify with `SHOW COLUMNS FROM users LIKE "password_hash";`
— `Null` should read `YES`. No restart is needed.

### Free DNS via DuckDNS (no registered domain)

DigitalOcean does not give out domain names, and free wildcard services like
`nip.io` cannot get Let's Encrypt certs. Use a free DuckDNS subdomain, which does
get valid HTTPS. DNS lives at DuckDNS, so Terraform does not manage it
(`manage_dns = false`).

1. At [duckdns.org](https://www.duckdns.org) sign in and create a subdomain, e.g.
   `sa62wellness` → `sa62wellness.duckdns.org`.
2. `terraform apply`, then read the reserved IP from the workflow output or run:
   `terraform output reserved_ip`.
3. In the DuckDNS dashboard set that subdomain's IP to the **reserved IP** and
   save. Do not use the Droplet's temporary IPv4 if it differs from
   `reserved_ip`.
4. Set GitHub config: Variable `MANAGE_DNS=false` and
   `API_DOMAIN=sa62wellness.duckdns.org`; leave `DOMAIN`/`SUBDOMAIN` unused.
5. Verify DNS before deploy:
   `dig +short sa62wellness.duckdns.org` must print the reserved IP.
6. Deploy. Caddy issues the cert over port 80 once the name resolves.
7. Android base URL: `https://sa62wellness.duckdns.org/`.

For a purchased domain instead, set `MANAGE_DNS=true`, `DOMAIN`, `SUBDOMAIN`, and
`API_DOMAIN=SUBDOMAIN.DOMAIN`, and point the domain's nameservers at DigitalOcean.

Production run command (on the Droplet):

```text
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Android and desktop clients point at the deployed HTTPS host through
`WELLNESS_API_BASE_URL`, for example:

```text
WELLNESS_API_BASE_URL=https://sa62wellness.duckdns.org/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
WELLNESS_API_BASE_URL=https://sa62wellness.duckdns.org/ dotnet run --project desktop-app/src/WellnessDesktop
```

## Optional AWS Hybrid Staging

AWS is optional and historical — DigitalOcean (above) is the chosen path. AWS is
not the required final demo path.

Recommended simplest AWS path:

- One EC2 instance.
- Docker Compose installed.
- Backend services deployed from the monorepo.
- MySQL either as container or RDS if the team has free-tier confidence.
- Ollama may remain local for final demo to avoid AWS GPU/cost issues.

AWS should be used for:

- Team integration practice.
- Backend smoke testing from Android over a shared URL.
- Presentation architecture discussion.

AWS should not be used for:

- Paid cloud LLM inference.
- A fragile final demo dependency.
- Secrets committed to GitHub.

## Acceptance Criteria

- Docker plan covers all backend/runtime services except Android.
- Environment variables are clearly specified.
- GitHub branch and PR workflow is clear.
- CI jobs are defined at behavior level.
- `SECURITY.md` defines Codex Security diff, scoped, and repository scan expectations.
- AWS is clearly optional hybrid staging.
- DigitalOcean is the chosen production path: Terraform infra, Ansible config +
  deploy from Actions, Caddy TLS, secrets in GitHub, firewall limited to
  22/80/443.

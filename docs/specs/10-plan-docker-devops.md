# 08 Docker And DevOps Spec

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-14, REQ-16, NFR-03, NFR-05 |
| Primary audience | Integration owner, backend owner, Python AI owner |
| Upstream specs | `04-plan-system-architecture.md`, `06-plan-api-contracts.md`, `08-plan-rag-ai-design.md` |
| Downstream specs | Docker Compose, GitHub Actions, setup docs |

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
- Manual Codex Security scan evidence is required in PR notes for security-sensitive changes; automated CI must not depend on a paid or cloud-only scanner.

Do not run heavyweight Ollama generation in CI by default. Use mocks or a lightweight health-check path so CI stays fast and reliable.

## DigitalOcean Deployment (Production)

The chosen cloud target is a single DigitalOcean Droplet running the Compose
stack. Infrastructure is managed with Terraform; application deployment runs
through GitHub Actions; secrets live in GitHub Actions secrets.

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

- Resources: Droplet (cloud-init installs Docker + prepares `/opt/wellness`),
  reserved IP, cloud firewall (inbound 22/80/443 only), DNS A record (optional via
  `manage_dns`), project grouping.
- Remote state in a DO Space (S3-compatible); see `infra/terraform/README.md` for
  the recommended Space configuration.

Deployment (`.github/workflows/`):

- `infra.yml` (manual) runs `terraform plan/apply/destroy`.
- `deploy.yml` (on push to `main`) builds `spring-backend` and `python-ai-service`
  images, pushes to GHCR, then over SSH ensures `/opt/wellness` exists and is
  owned by the `deploy` user, ships compose/Caddy/knowledge-base files, writes
  `.env` from secrets, pulls images, runs the prod overlay, ensures the Ollama
  models are present, and prebuilds the Python RAG vector index before the mobile
  demo path uses AI.
- Both use a `production` GitHub Environment for an approval gate.

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

The built-in `GITHUB_TOKEN` (no setup) is used by `deploy.yml` to push images to
GHCR. Never store any of these in the repo, Terraform state, or cloud-init.

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

Android points at `https://api.<domain>/` via `WELLNESS_API_BASE_URL`.

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
- DigitalOcean is the chosen production path: Terraform infra, Actions deploy,
  Caddy TLS, secrets in GitHub, firewall limited to 22/80/443.

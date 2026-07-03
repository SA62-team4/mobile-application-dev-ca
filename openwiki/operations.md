# Operations, Docker, CI, and Deployment

This repository has a fairly detailed operational story. The same project supports local development, CI validation, optional quality scanning, and deployment automation.

## Local startup

The README documents the main local path:

- copy `.env.example` to `.env`
- start MySQL and Ollama with Docker Compose
- pull the local models
- build and start the stack

Source references:

- `README.md`
- `RUN_PROJECT_ON_DOCKER.md`
- `docs/setup.md`
- `docs/specs/10-plan-docker-devops.md`

## Docker Compose roles

The compose setup is intended to cover:

- MySQL
- Spring Boot backend
- Python AI service
- Ollama
- optional Adminer
- optional .NET backup backend for rehearsal

The Docker/DevOps spec also defines the host-port strategy, local model names, and the optional backup-mode conventions.

## CI and quality gates

GitHub Actions workflows are central to the repo's automation. Recent history shows attention to:

- test coverage and quality gates
- semgrep supply-chain scanning via lockfiles
- SonarQube Community Build integration
- deployment automation using Terraform and Ansible

Relevant paths:

- `.github/workflows/`
- `docs/sonarqube-community-build.md`
- `docs/specs/14-validate-quality-gates.md`
- `docs/specs/15-validate-test-and-demo-plan.md`
- `docs/specs/10-plan-docker-devops.md`

## Google SSO operational detail

`docs/google-sso-setup.md` is unusually important operationally because it documents a shared debug keystore, Google OAuth client setup, and backend defaults needed to prevent local sign-in failures.

If a change affects auth setup, this file and the backend config should be reviewed together.

## Deployment notes

The repo appears to support at least two operational tracks:

- local/demo runtime using Docker Compose
- deployment automation for cloud-hosted environments

The architecture and DevOps specs indicate the canonical production-style path uses a single host running the full stack with Caddy/TLS and GitHub Actions + Ansible/Terraform for provisioning and deployment.

## Change guidance

When editing operational code or docs:

1. Check the specs before changing compose files or workflows.
2. Keep secrets out of the repo.
3. Make sure local demo commands stay simple and documented.
4. Prefer adding or updating docs rather than encoding assumptions only in scripts.

## Best starting points

- `README.md`
- `RUN_PROJECT_ON_DOCKER.md`
- `docs/setup.md`
- `docs/sonarqube-community-build.md`
- `.github/workflows/`
- `docs/specs/10-plan-docker-devops.md`

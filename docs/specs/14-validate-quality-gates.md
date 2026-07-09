# 14 Validation Gates

<!-- @author Tiong Zhong Cheng -->

## Purpose

This file defines how to verify that implementation output matches the specs. It corresponds to the Spec Kit **Validate** step.

Validation must compare the built system against the constitution, requirements, traceability matrix, and subsystem specs.

## Gate 1: Spec Completeness

Run before implementation starts.

Checklist:

- Constitution exists and has no unresolved project-level contradiction.
- Requirement IDs are defined.
- Clarification log has defaults for open questions.
- Architecture includes the canonical technology stack baseline.
- Architecture, ERD, API, Android UI, RAG, agent, Docker, and test specs exist.
- Android UI spec links to the Figma design handoff when visual screens are available.
- Traceability matrix maps every requirement to evidence and verification.
- Implementation tasks reference requirement IDs.

Pass condition:

- Team can start implementation without deciding architecture from scratch.

## Gate 2: PR-Level Validation

Run for every implementation PR later.

Checklist:

- PR lists affected task IDs and requirement IDs.
- PR updates specs if behavior changed.
- Android UI PRs compare XML screens against the Figma UI spec where applicable.
- Android UI PRs verify there are no overlapping labels, fields, cards, buttons, or navigation controls on compact `360dp` portrait layouts.
- Tests match the changed subsystem.
- Framework/runtime family, default local AI model, storage engine, API-client
  approach, or Docker topology changes update the technology stack baseline in
  `04-plan-system-architecture.md` and the controlling component spec.
- SonarQube scan steps are present and either publish to the configured dashboard
  or skip cleanly when `SONAR_HOST_URL`/`SONAR_TOKEN` are unavailable. The
  configured project set includes Spring, Android, Python, and the optional
  `.NET Backup API`.
- Spring backend SonarQube evidence includes the JaCoCo XML report generated at
  `spring-backend/target/site/jacoco/jacoco.xml` from a Maven `verify` run.
- Android SonarQube evidence includes the JaCoCo XML coverage report generated at
  `android-app/app/build/reports/coverage/test/debug/report.xml` from the
  `createDebugUnitTestCoverageReport` Gradle task (enabled via `enableUnitTestCoverage`),
  referenced by `sonar.coverage.jacoco.xmlReportPaths` in
  `android-app/sonar-project.properties`.
- Android release traffic is TLS-only: the manifest applies
  `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"`
  by default, permitting clear-text HTTP only for local development hosts
  (`10.0.2.2`, `localhost`, `127.0.0.1`).
- Security-sensitive PRs include Codex Security diff scan evidence according to `SECURITY.md`.
- No paid/cloud LLM dependency is introduced.
- No direct Android-to-MySQL or Android-to-Python path is introduced.
- No secrets are committed.
- Valid high or critical Codex Security findings are fixed or explicitly deferred with rationale before merge.
- Optional `.NET Backup API` changes state that Spring Boot remains canonical for `REQ-08`.
- Optional privacy (`REQ-23`/`S-03`) PRs include account-export ownership tests, account-deletion transaction/post-delete-token tests, Android manual evidence for export/delete states, and a Codex Security diff scan because account deletion changes user-data handling.
- Optional security-hardening (`S-04`) PRs keep Spring and the `.NET Backup API` on the same throttle thresholds and the same `429` + `Retry-After` contract, check the lockout before any credential comparison, never record failures for unknown or deleted emails, and include a Codex Security diff scan because they change authentication behavior.

Pass condition:

- Reviewer can trace the code change back to one or more specs.

## Gate 3: Integration Validation

Run when backend, Android, Python AI, and Docker work are joined.

Checklist:

- Android can register, log in, and log out.
- Android login and register screens match the Figma layout and show inline validation without overlapping content.
- JWT protects all non-auth APIs.
- Wellness CRUD works from Android to MySQL.
- Records, add/edit record, chat, recommendations, and profile screens match the Figma layout at compact portrait width.
- Chatbot works through Spring Boot and Python RAG service.
- RAG responses include source snippets.
- Python agent retrieves recent records, analyses trends, and saves recommendations.
- Docker Compose starts required backend/runtime services.
- SonarQube Community Build, if used for team evidence, runs from
  `docker-compose.sonar.yml` outside the main demo stack and the team can access
  the dashboard.
- Optional backup validation: `.NET Backup API` health/status endpoints match Spring and backup mode uses the same MySQL schema and internal service token header.
- Optional desktop validation (`REQ-21`): the .NET Avalonia desktop client completes auth, wellness CRUD, chatbot, and recommendation flows against the same running Spring Boot backend, with loading/empty/success/error states and friendly error messages.
- Optional privacy validation (`REQ-23`): Profile opens the Privacy screen; export returns only the signed-in user's JSON payload; delete account removes the user's MySQL-owned data, clears Android local auth, and prevents the old JWT from accessing protected endpoints.

Pass condition:

- The main demo flow works end to end on a clean setup.

## Gate 4: Demo Validation

Run before video recording and submission.

Checklist:

- Demo script fits 15 minutes.
- Figma UI spec is available as visual evidence for Android design intent.
- Seed data exists and shows meaningful trends.
- Ollama models are pulled before demo.
- Codex Security repository or scoped scans have been run for changed runtime components, with findings fixed or documented.
- SonarQube dashboard evidence is captured at
  `https://sa62wellness-sonar.duckdns.org` for configured components, including
  quality gate status and issue summary.
- PlantUML diagrams render or exported images are available.
- ERD matches implemented schema.
- API docs match implemented endpoints.
- Author comments are present in classes or key methods.
- Final zip contains one integrated solution and video demo.
- Optional `.NET Backup API` is mentioned only as backup evidence and does not replace the Spring Boot demo path.
- Optional .NET desktop client, if shown, is presented as a bonus additional client and does not replace the Android demo path.
- Optional privacy stretch, if shown, is presented as local/private AI trust evidence and does not consume time needed for mandatory Android, Spring, MySQL, RAG, Python agent, and Docker flows.

Pass condition:

- Team can present the app against the marking criteria without relying on unstable optional features.

## Gate 5: Deployment Validation

Run when deploying to DigitalOcean (optional for the local demo path).

Checklist:

- `terraform apply` provisions the Droplet, reserved IP, firewall, and DNS cleanly.
- Cloud firewall exposes only inbound 22/80/443; data/AI services are not public.
- `deploy.yml` builds/pushes images then runs `ansible-playbook` behind the `production` environment approval.
- The Ansible play is idempotent: a second run reports no changes for unchanged config.
- `ansible-playbook --syntax-check` and `ansible-lint` pass in CI (the `ansible` job).
- `https://api.<domain>/actuator/health` returns `UP` with a valid certificate.
- No secrets are committed to the repo, Terraform state, cloud-init, or the playbooks; all live in GitHub Actions secrets and are passed to Ansible via the environment.
- If SonarQube is deployed to DigitalOcean, the combined `infra.yml` provisions
  the dedicated Droplet and `deploy.yml` with `target=sonar` runs
  `site.yml --limit sonar`; the dashboard is reachable over HTTPS on
  `https://sa62wellness-sonar.duckdns.org`, team members have non-admin access
  through local accounts or GitHub App-backed sign-in, and port `9000` is not
  publicly exposed.
- Full Android flow works against the HTTPS domain; Droplet reboot restores the stack with models persisted.

Pass condition:

- A push to `main` deploys the stack and the HTTPS health check passes.

## Spec Conformance Report

Before final submission, prepare a short report with:

- Requirement IDs completed.
- Evidence location for each major feature.
- Tests run.
- Known limitations.
- Optional features included or skipped.
- Optional backup backend evidence, if included, with Spring parity checks and known limitations.

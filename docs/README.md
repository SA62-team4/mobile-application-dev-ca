# Documentation

Start with the spec-driven development pack:

- `specs/00-spec-kit-index.md`
- `specs/01-constitution-principles.md`
- `specs/02-specify-project-requirements.md`
- `specs/03-clarify-decisions-and-edge-cases.md`
- `specs/11-plan-implementation-roadmap.md`
- `specs/12-tasks-implementation-backlog.md`
- `specs/13-analyze-traceability-matrix.md`
- `specs/14-validate-quality-gates.md`

The current repository phase is active implementation. Specs remain the source of truth and should be updated before behavior or contracts change.

## Spec-Driven Workflow

1. Constitution: read `specs/01-constitution-principles.md`.
2. Specify: read requirement IDs in `specs/02-specify-project-requirements.md`.
3. Clarify: resolve ambiguity in `specs/03-clarify-decisions-and-edge-cases.md`.
4. Plan: read the controlling architecture, ERD, API, UI, AI, Docker, and roadmap specs.
5. Tasks: use `specs/12-tasks-implementation-backlog.md` for implementation-ready work units.
6. Implement: generate/refine code and tests against the specs only after implementation begins.
7. Validate: check `specs/13-analyze-traceability-matrix.md`, `specs/14-validate-quality-gates.md`, and `specs/15-validate-test-and-demo-plan.md`.

## PlantUML Preview

The specs use PlantUML diagram blocks. This workspace is configured for PlantUML server rendering through `.vscode/settings.json` because some Markdown preview extensions require `plantuml.server`.

If VS Code still shows `No PlantUML server, specify one with "plantuml.server"`, reload the VS Code window and reopen the Markdown preview.

The committed default uses the public PlantUML server:

```json
{
  "plantuml.render": "PlantUMLServer",
  "plantuml.server": "https://www.plantuml.com/plantuml"
}
```

If you do not want diagrams sent to a public service, run a local PlantUML server later and change `plantuml.server` to that local URL, for example `http://localhost:8080`.

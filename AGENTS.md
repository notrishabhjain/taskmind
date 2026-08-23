# AGENTS.md — TaskMind

Guidance for AI coding agents (and humans) working in this repository.

## Project purpose

TaskMind is a native Android task-management application designed around a
reliable capture pipeline: messages, calls, and manual input all flow through
one auditable intake funnel into a local-first task store. The repository
currently contains the data/domain layer plus the core task manager UI
(Milestones 1–2): domain models, the Room database, repositories, title
normalisation, evidence validation, confidence gating, the Task Intake service,
state transitions, and the task manager UI (Today/Upcoming/Overdue/Completed/
Archived/All with create/edit flows) plus the Review Inbox (accept routes through
the intake funnel via `ReviewService`; dismissals are logged) and a bounded
Activity Log screen fed by `ActivityLogRepository.observeRecent`. Bottom-tab
navigation lives in `MainActivity` (manual state, no nav library). CI builds and verifies every change.

## Core principles (non-negotiable)

1. **Never silently lose a capture.** Inputs that cannot become tasks are
   persisted or logged as rejected/review/failed outcomes — never dropped.
2. **Precision beats recall.** Low-confidence extractions go to review, not to
   the task list.
3. **Never fabricate.** Automatically derived tasks require verifiable source
   evidence; provenance fields are preserved, never invented.
4. **Silent by default.** No notifications from background processing yet.
5. **Everything is visible.** Meaningful outcomes are recorded in the bounded
   Activity Log (`activity_log`, newest 300 entries retained).

## Single intake rule

Every task-producing source — future notification capture, call capture,
review acceptance, anything new — MUST call `TaskIntakeService.submit()`.
There is exactly one funnel: validate → normalise → confidence gate → evidence
validation → deduplication → persist → activity log. Do NOT insert `Task`
rows through any other path; do not add a second task-writing service.

## Architecture

- Single-module Android application (`:app`). Do not introduce additional modules
  until there is a concrete need; revisit at the first feature that demands it.
- Package layout under `app/src/main/java/com/notrishabhjain/taskmind/`:
  - `MainActivity.kt` — single entry-point Activity; hosts Compose content only.
  - `ui/theme/` — Material 3 theme: `Color.kt`, `Theme.kt`, `Type.kt`.
  - `ui/<feature>/` — one package per screen/feature (`ui/tasks`,
    `ui/editor`), containing:
    - `XxxScreen.kt` — stateless composable receiving a `XxxUiState` parameter.
    - `XxxUiState.kt` — plain immutable state data class (unit-testable without
      Android or instrumentation).
    - `XxxViewModel.kt` — holds UI state, delegates every write to
      `TaskIntakeService` (creation) or `TaskService` (transitions/edits).
  - `domain/model/` — pure-Kotlin models (`Task`, `ReviewItem`,
    `ActivityLogEntry`) and enums (`Priority`, `TaskStatus`, `SourceType`,
    `SyncState`, `InferenceOrigin`). `java.time.Instant` only; no Android or
    Room types.
  - `domain/intake/` — `TaskIntakeService` (the single funnel),
    `TitleNormalizer`, `EvidenceValidator`, `ConfidenceGate` (+ configurable
    `ConfidenceThresholds`: auto-create >= 0.75, reject < 0.40, missing/invalid
    -> review), `TaskProposal` factories (`manual`, `extracted`,
    `fromAcceptedReview`), `IntakeOutcome`.
  - `domain/repository/` — small suspend interfaces (`TaskRepository`,
    `ReviewRepository`, `ActivityLogRepository`, `ProjectTagRepository`);
    domain never sees Room types.
  - `domain/service/TaskService` — state transitions and edits
    (complete/reopen/archive/unarchive/delete/update); every mutation updates
    `updatedAt`, writes an Activity Log entry, and preserves provenance.
  - `domain/time/TimeProvider` — injectable clock; production impl uses
    `Instant.now()`; tests use fixed providers. Never call `Instant.now()`
    inside business logic.
  - `data/db/` — Room database (`taskmind.db`, version 1), entities under
    `entity/`, DAOs under `dao/`.
  - `data/mapper/EntityMappers.kt` — explicit entity<->domain mapping; enum
    columns parse strictly (unknown value throws).
  - `data/repository/Room*Repository` — the only implementations of the domain
    repository interfaces.
  - `di/AppContainer` — hand-rolled composition root constructed by
    `TaskMindApplication`. No DI framework; extend it manually as wiring grows.
- UI state flows one way: `UiState -> Screen`. Screens never own business logic.
- Resources live under `app/src/main/res`; user-facing strings are always in
  `strings.xml` / plurals, never hardcoded.

## Data rules agents must preserve

- **Deduplication** is enforced twice: logically via
  `TaskRepository.findByLogicalKey(sourceType, sourceRef, titleKey)` in the
  intake service, and structurally by the unique index
  `index_tasks_source_key` on `(sourceType, sourceRef, titleKey)`. SQLite treats
  NULLs as distinct in unique indexes, which is exactly why manual tasks
  (`sourceRef = NULL`) may repeat while generated tasks cannot duplicate.
- **Provenance** (`sourceType`, `sourceRef`, `sourceLabel`, `sourceApp`,
  `evidence`, `confidence`, `inferenceOrigin`, `modelId`) must survive edits;
  only intake writes it initially. Manual tasks legitimately have nulls there —
  do not fabricate values.
- **Evidence validation** (`EvidenceValidator`) compares whitespace-collapsed,
  lowercased containment and never repairs or paraphrases evidence.
- **Activity Log** appends always go through `ActivityLogRepository.append`,
  which trims the table to `RETENTION_LIMIT` (300) rows inside one transaction.
- `ReviewItem` is currently write-path foundation only (created by the funnel's
  review branch). Accept/dismiss workflows come in a later milestone and MUST
  route acceptance back through `TaskIntakeService`.

## Technology stack

Pinned in `gradle/libs.versions.toml` (single source of truth for versions):

| Component | Version | Notes |
| --- | --- | --- |
| Gradle | 9.5.0 | Wrapper-pinned + `distributionSha256Sum` in `gradle-wrapper.properties` |
| Android Gradle Plugin | 9.3.0 | Requires JDK 17+, Gradle >= 9.5 |
| Kotlin | 2.3.21 | Applied via built-in Kotlin support in AGP 9 (see decisions below) |
| Jetpack Compose | BOM 2026.08.00 | ui, material3, tooling via BOM |
| Room | 2.8.4 | runtime + ktx; compiler via KSP |
| KSP | 2.3.11 | Runs Room's annotation processor under built-in Kotlin |
| compileSdk / targetSdk | 37 / 36 | minSdk 26 (Android 8.0+); java.time usable without desugaring |
| JVM target | 17 | Set once in `compileOptions`; built-in Kotlin follows it |
| Unit tests | JUnit 4.13.2 | JVM-only tests in `app/src/test` |

## Important architectural decisions

1. **CI-first development model.** The primary development machine has no JDK,
   Android SDK, or Gradle installed by design. GitHub Actions is the authoritative
   build environment. Never add steps that require a locally installed toolchain;
   never assume builds can be verified locally.
2. **AGP 9 built-in Kotlin.** AGP >= 9.0 compiles Kotlin itself; the
   `org.jetbrains.kotlin.android` plugin is intentionally NOT applied anywhere.
   The only Kotlin-related plugin is `org.jetbrains.kotlin.plugin.compose`
   (versioned with `kotlin`). If you need custom Kotlin compiler options, use a
   top-level `kotlin { compilerOptions { } }` block, not `kotlinOptions { }`.
3. **Version Catalog only.** All dependency/plugin versions go through
   `gradle/libs.versions.toml`. Never inline versions in `.gradle.kts` files.
4. **Reproducibility pins.** The wrapper distribution URL is checksum-pinned
   (`distributionSha256Sum`). Update it whenever changing the Gradle version.
5. **Edge-to-edge Compose UI.** Activities call `enableEdgeToEdge()`; screens use
   `Scaffold` and consume its `innerPadding`.
6. **No secrets in git.** Signing keys, API keys, tokens, and passwords must never
   be committed. Release signing is not configured yet; it will use GitHub
   Secrets later.
7. **R8 enabled for release** from day one (`proguard-android-optimize.txt` +
   `app/proguard-rules.pro`).

## Build commands

Run on CI (or any machine with JDK 17+; the wrapper downloads Gradle):

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug              # Android lint for the debug variant
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug testDebugUnitTest assembleDebug   # full CI check set
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Testing commands

```bash
./gradlew testDebugUnitTest            # all unit tests
./gradlew testDebugUnitTest --tests "com.notrishabhjain.taskmind.ui.home.HomeUiStateTest"
```

Unit tests are pure-JVM (`app/src/test`, JUnit 4). Keep `UiState` classes free of
Android framework dependencies so they stay testable this way.

## Conventions for future agents

- Follow the existing structure: new screens go in `ui/<feature>/` with a paired
  `UiState`; shared design system changes go in `ui/theme/`.
- Kotlin code style: official (`kotlin.code.style=official`). No wildcard imports,
  no unused imports, no commented-out code.
- Do not add comments explaining obvious code; document decisions in this file or
  PR descriptions instead.
- Bump versions ONLY inside `libs.versions.toml` (and `gradle-wrapper.properties`
  for Gradle). Check compatibility before bumping:
  - AGP <-> Gradle <-> JDK matrix: https://developer.android.com/build/releases/gradle-plugin
  - Kotlin <-> Compose: Compose Compiler plugin version must equal the Kotlin version.
  - Compose BOM <-> compileSdk requirements: newer BOMs may raise minimum compileSdk.
- Regenerating/updating wrapper scripts requires a machine with Java; otherwise
  fetch them from the matching `v<version>` tag of github.com/gradle/gradle.
- Lint must stay green: `lint.abortOnError = true` in `app/build.gradle.kts`.
  Fix findings or add narrowly-scoped suppressions with justification.
- Commit messages: imperative mood, concise subject line (e.g. "Add task list screen").
- The default branch is `main`; CI runs on pushes to `main` and on all PRs to it.

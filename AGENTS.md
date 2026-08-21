# AGENTS.md — TaskMind

Guidance for AI coding agents (and humans) working in this repository.

## Project purpose

TaskMind is a native Android task-management application. This repository currently
contains the project foundation: an installable app shell with a Material 3 home
screen, reproducible Gradle build configuration, and CI that builds and verifies
every change. Business features (tasks, storage, sync) will be added on top of
this foundation.

## Architecture

- Single-module Android application (`:app`). Do not introduce additional modules
  until there is a concrete need; revisit at the first feature that demands it.
- Package layout under `app/src/main/java/com/notrishabhjain/taskmind/`:
  - `MainActivity.kt` — single entry-point Activity; hosts Compose content only.
  - `ui/theme/` — Material 3 theme: `Color.kt`, `Theme.kt`, `Type.kt`.
  - `ui/<feature>/` — one package per screen/feature (`ui/home` today), containing:
    - `XxxScreen.kt` — stateless composable receiving a `XxxUiState` parameter.
    - `XxxUiState.kt` — plain immutable state data class (unit-testable without
      Android or instrumentation).
  - Future layers (domain/data) get their own top-level packages when introduced.
- UI state flows one way: `UiState -> Screen`. Screens never own business logic.
- Resources live under `app/src/main/res`; user-facing strings are always in
  `strings.xml` / plurals, never hardcoded.

## Technology stack

Pinned in `gradle/libs.versions.toml` (single source of truth for versions):

| Component | Version | Notes |
| --- | --- | --- |
| Gradle | 9.5.0 | Wrapper-pinned + `distributionSha256Sum` in `gradle-wrapper.properties` |
| Android Gradle Plugin | 9.3.0 | Requires JDK 17+, Gradle >= 9.5 |
| Kotlin | 2.3.21 | Applied via built-in Kotlin support in AGP 9 (see decisions below) |
| Jetpack Compose | BOM 2026.08.00 | ui, material3, tooling via BOM |
| compileSdk / targetSdk | 37 / 36 | minSdk 26 (Android 8.0+) |
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

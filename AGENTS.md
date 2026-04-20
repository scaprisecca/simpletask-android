# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app in `app/`. Core code lives in `app/src/main/java/nl/mpcjanssen/simpletask`, with task parsing and filtering under `task/`, Room database code under `dao/`, and storage helpers under `remote/` and `util/`. Shared resources live in `app/src/main/res`, while in-app help lives in `app/src/main/assets`.

Flavor-specific code is split by backend in `app/src/cloudless`, `app/src/nextcloud`, `app/src/webdav`, `app/src/encrypted`, and `app/src/dropbox`. JVM unit tests live in `app/src/test/java`. Use `README.md` and `CONTRIBUTE.md` for product context and maintainer expectations.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root:

- `./scripts/build_cloudless.sh` is the preferred local helper for cloudless builds. It pins Gradle to JDK 11, exports the Android SDK path, creates `local.properties` if missing, and defaults to `assembleCloudlessDebug`.
- `./gradlew assembleCloudlessDebug` builds the default local-file flavor.
- `./gradlew assembleNextcloudDebug` builds a remote-sync variant.
- `./gradlew test` runs JVM unit tests in `app/src/test/java`.
- `./gradlew lint` runs Android lint with the project’s configured disabled checks.
- `./gradlew installCloudlessDebug` installs the debug APK on a connected device or emulator.

Common helper-script invocations:

- `./scripts/build_cloudless.sh` builds the default local-file flavor.
- `./scripts/build_cloudless.sh installCloudlessDebug` builds and installs on a connected device or emulator.
- `./scripts/build_cloudless.sh assembleCloudlessRelease -- --stacktrace` runs a release build with extra Gradle flags.

Release signing is optional. Sample local config lives in `gradle.properties.sample` and `local.properties.sample`. Do not commit machine-local `local.properties` changes.

## Coding Style & Naming Conventions
Follow the existing style in the touched file. Kotlin and Java both use 4-space indentation and keep package names lower-case (`nl.mpcjanssen.simpletask`). Class names use `UpperCamelCase`; methods, properties, and local variables use `lowerCamelCase`; constants use `UPPER_SNAKE_CASE`.

Keep flavor-specific behavior in the matching source set instead of branching in shared code. Prefer small, focused changes in existing packages over new abstractions.

## Testing Guidelines
Tests use JUnit 4-style `TestCase` classes. Add JVM tests next to related code under `app/src/test/java/...`, and name them `*Test`. Cover task parsing, filtering, and utility behavior with small deterministic cases. Run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subjects such as `fix circleci error` and issue-linked summaries. Keep commits focused and descriptive. Before a PR, check open issues and discuss larger changes first; `CONTRIBUTE.md` explicitly asks for early discussion on enhancements.

PRs should explain the affected flavor(s), link the issue when relevant, and include screenshots for UI-visible changes. Do not commit machine-local files, signing credentials, or values derived from `local.properties`, `~/.gradle/gradle.properties`, or `secrets/`.

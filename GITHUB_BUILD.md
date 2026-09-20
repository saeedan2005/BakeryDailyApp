# BakeryDaily - GitHub Cloud Build

This package contains the complete Android `app` module and is prepared for GitHub Actions.

## Important correction from the previous build

The earlier GitHub Actions failure occurred in `android-actions/setup-android@v3` while it attempted to find the legacy Android SDK `tools` package. This workflow deliberately does **not** use that action.

The workflow uses the Android SDK already provided by the GitHub-hosted Ubuntu runner and only uses `sdkmanager` to ensure `platform-tools`, Android 35, and Build Tools 35.0.0 are available.

It also verifies that `app/build.gradle.kts` and the Android source tree exist before Gradle starts, preventing the previous empty-`app` situation from reaching the build step.

## Build

GitHub → Actions → Build Android APK → Run workflow.

Artifacts:
- `BakeryDaily-Debug-APK`
- `BakeryDaily-Release-APK`

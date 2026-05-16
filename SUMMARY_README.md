# LocoPillars — Editor Upgrade Summary

This file summarizes all editor-related changes delivered in this update wave.

## 1) Advanced editor messaging (English)

- Improved onboarding and guidance messages for admins in edit mode.
- Added clearer “next step” flow messages (map selection, edit start, settings).
- Improved clarity around hotbar behavior and exit flow.

## 2) Data template setup and diagnostics

### `/editor setup` (advanced output)
- Now reports explicit absolute paths for:
  - `Data/Games`
  - `Data/Schems`
- Shows setup counters (folders/config migration/copying/mirroring).
- Shows readiness totals for templates (ready vs total).
- Prints per-template status preview.

### `/editor doctor` (new)
- Added a dedicated diagnostics command to check template readiness.
- Validates each `Data/Games/<name>.yml` against:
  - `Data/Schems/<name>.schem`
  - `Data/Schems/<name>/*.schem`
  - fallback `maps/<name>/region.schem`
- Gives clear “READY” / “MISSING_SCHEM” style feedback.

### `./Data/Games/` handling
- Setup flow explicitly bootstraps and validates `Data/Games`.
- Added generated guidance files:
  - `Data/Games/README_SETUP.txt`
  - `Data/Schems/README_SETUP.txt`
- This makes required file naming/matching rules clear for admins.

## 3) Command surface cleanup requested

- Removed `/editor enable` command entry.
- Removed `/editor disable` command entry.
- Updated help text, tab completion, and `plugin.yml` usage to match.
- Updated references to use:
  - `/editor edit` (auto-enables edit mode when needed)
  - `/editor toggle` or slot 8 barrier to exit edit mode

## 4) Documentation updates

- Updated `src/main/resources/README_ADMIN_EDITOR.md` to match current behavior:
  - edit mode activation via `/editor edit`
  - exit via `/editor toggle` or barrier
  - diagnostics via `/editor doctor`
  - setup coverage for `Data/Games` and `Data/Schems`

## 5) Validation notes

- Build command used:
  - `./gradlew build -Dorg.gradle.java.home=/usr/lib/jvm/temurin-17-jdk-amd64`
- In this sandbox run, dependency resolution may fail due to external DNS/network restrictions.
- Code changes were still kept focused and consistent with current editor architecture.

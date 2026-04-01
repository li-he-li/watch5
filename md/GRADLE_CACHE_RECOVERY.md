# Gradle Cache Recovery (Windows)

## Symptom

Build fails on resource tasks with errors like:

- `The contents of the immutable workspace ... transforms/.../workspace have been modified`
- `:shared:compileDebugLibraryResources`
- `:phone-app:compileDebugNavigationResources`
- `:wear-app:compileDebugNavigationResources`

## Root Cause

Corrupted or externally modified Gradle transform cache (commonly under `%USERPROFILE%\.gradle\caches\9.2.1\transforms`).

## One-Command Repair

From project root:

```powershell
.\scripts\repair-gradle-cache.ps1
```

If still failing:

```powershell
.\scripts\repair-gradle-cache.ps1 -CleanAllTransforms
```

## Verification

```powershell
.\gradlew :shared:compileDebugLibraryResources :phone-app:compileDebugNavigationResources :wear-app:compileDebugNavigationResources
```

## Prevention Added In This Repo

- Wrapper scripts now default `GRADLE_USER_HOME` to project-local `.gradle-user-home` (when env var is not preset).
- This isolates each project from broken global cache reuse.

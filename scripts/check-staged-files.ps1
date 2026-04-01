$ErrorActionPreference = "Stop"

$blockedPathPatterns = @(
    '(^|/)build/',
    '(^|/)\.gradle/',
    '(^|/)\.tmp/',
    '(^|/)\.tmp-watch/',
    '(^|/)\.android/',
    '(^|/)\.android-home/',
    '(^|/)\.adbhome/',
    '(^|/)watch5-clean-export/',
    '(^|/)logs/'
)

$textFileExtensions = @(
    '.kt', '.kts', '.java', '.md', '.txt', '.xml', '.gradle',
    '.properties', '.ps1', '.yml', '.yaml', '.gitignore', '.sh'
)

$stagedFiles = @(git diff --cached --name-only --diff-filter=ACMR)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read staged files."
}

if ($stagedFiles.Count -eq 0) {
    Write-Host "pre-commit: no staged files"
    exit 0
}

$violations = New-Object System.Collections.Generic.List[string]

foreach ($file in $stagedFiles) {
    $normalized = $file -replace '\\', '/'

    foreach ($pattern in $blockedPathPatterns) {
        if ($normalized -match $pattern) {
            $violations.Add("Refusing to commit generated or local artifact: $file")
            break
        }
    }

    $extension = [System.IO.Path]::GetExtension($file)
    if ($textFileExtensions -notcontains $extension -and $file -notin @('.gitignore', 'README.md', 'AGENTS.md')) {
        continue
    }

    $content = git show ":$file" 2>$null
    if ($LASTEXITCODE -ne 0) {
        continue
    }

    if ($content -match '(^|\r?\n)(<<<<<<< |=======|>>>>>>> )') {
        $violations.Add("Merge conflict marker found in staged file: $file")
    }

    if ($content -match "[ \t]+(\r?\n)") {
        $violations.Add("Trailing whitespace found in staged file: $file")
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    throw "pre-commit checks failed."
}

Write-Host "pre-commit: staged-file checks passed"

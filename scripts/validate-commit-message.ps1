param(
    [Parameter(Mandatory = $true)]
    [string]$CommitMessageFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $CommitMessageFile)) {
    throw "Commit message file not found: $CommitMessageFile"
}

$firstLine = (Get-Content -Path $CommitMessageFile | Select-Object -First 1).Trim()
$pattern = '^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)(\([a-z0-9._/-]+\))?!?: .+'

if ($firstLine -notmatch $pattern) {
    Write-Error "Invalid commit message: $firstLine"
    Write-Host "Expected conventional commit format, e.g. feat(wear): add direct sensor fallback"
    exit 1
}

Write-Host "commit-msg: message format passed"

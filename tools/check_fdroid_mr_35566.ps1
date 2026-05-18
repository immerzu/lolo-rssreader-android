$ErrorActionPreference = "Stop"

$mrUrl = "https://gitlab.com/fdroid/fdroiddata/-/merge_requests/35566"
$projectPath = "fdroid/fdroiddata"
$projectId = [System.Uri]::EscapeDataString($projectPath)
$mrIid = 35566
$apiBase = "https://gitlab.com/api/v4/projects/$projectId/merge_requests/$mrIid"
$gitlabToken = $env:GITLAB_TOKEN

function Write-Section($title) {
    Write-Host ""
    Write-Host ("=" * 80)
    Write-Host $title
    Write-Host ("=" * 80)
}

function Get-Json($url) {
    $headers = @{ "User-Agent" = "Codex-MR-Check" }
    if (-not [string]::IsNullOrWhiteSpace($gitlabToken)) {
        $headers["PRIVATE-TOKEN"] = $gitlabToken
    }
    return Invoke-RestMethod -Uri $url -Method Get -Headers $headers
}

Write-Section "F-Droid MR Check"
Write-Host "MR: $mrUrl"
Write-Host "Zeit: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
if ([string]::IsNullOrWhiteSpace($gitlabToken)) {
    Write-Host ""
    Write-Host "FEHLT: Umgebungsvariable GITLAB_TOKEN"
    Write-Host "GitLab liefert fuer diese API hier 401 Unauthorized ohne Token."
    Write-Host ""
    Write-Host "Bitte in derselben PowerShell vor dem Aufruf einmal setzen:"
    Write-Host '$env:GITLAB_TOKEN = "DEIN_GITLAB_TOKEN"'
    Write-Host 'powershell -ExecutionPolicy Bypass -File "F:\Codex\RSS_Reader_Android\rss_reader_full_project\tools\check_fdroid_mr_35566.ps1"'
    exit 1
}

try {
    $mr = Get-Json $apiBase
    $notes = Get-Json "$apiBase/notes?sort=desc&order_by=updated_at&per_page=20"
    $commits = Get-Json "$apiBase/commits?per_page=10"
    $pipelines = Get-Json "$apiBase/pipelines?per_page=10"
} catch {
    Write-Host ""
    Write-Host "API-ABFRAGE FEHLGESCHLAGEN"
    Write-Host $_.Exception.Message
    Write-Host ""
    Write-Host "Pruefe bitte, ob GITLAB_TOKEN gesetzt und gueltig ist."
    exit 1
}

Write-Section "MR Status"
Write-Host "Titel: $($mr.title)"
Write-Host "State: $($mr.state)"
Write-Host "Draft: $($mr.draft)"
Write-Host "Merge Status: $($mr.merge_status)"
Write-Host "Detailed Merge Status: $($mr.detailed_merge_status)"
Write-Host "Source Branch: $($mr.source_branch)"
Write-Host "Target Branch: $($mr.target_branch)"
Write-Host "Web URL: $($mr.web_url)"
Write-Host "Updated At: $($mr.updated_at)"
Write-Host "Author: $($mr.author.name) (@$($mr.author.username))"

Write-Section "Pipelines (neueste zuerst)"
foreach ($pipeline in $pipelines) {
    Write-Host ("- Pipeline #{0} | status={1} | sha={2} | updated={3}" -f $pipeline.id, $pipeline.status, $pipeline.sha, $pipeline.updated_at)
}

Write-Section "Commits (neueste zuerst)"
foreach ($commit in $commits) {
    $short = if ($commit.id.Length -ge 8) { $commit.id.Substring(0, 8) } else { $commit.id }
    Write-Host ("- {0} | {1} | {2}" -f $short, $commit.created_at, $commit.title)
}

Write-Section "Notes / Kommentare (neueste 20)"
foreach ($note in $notes) {
    $body = ($note.body -replace "`r", " " -replace "`n", " ").Trim()
    if ($body.Length -gt 300) {
        $body = $body.Substring(0, 300) + "..."
    }
    Write-Host ("- {0} | {1} (@{2}) | system={3}" -f $note.updated_at, $note.author.name, $note.author.username, $note.system)
    Write-Host ("  {0}" -f $body)
}

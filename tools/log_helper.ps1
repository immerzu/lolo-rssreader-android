param(
    [ValidateSet("prepare", "collect")]
    [string]$Mode = "collect",
    [string]$PackageName = "de.lolo.rssreader",
    [string]$OutputDir = "F:\Codex\RSS_Reader_Android\Ausgabe_APK",
    [string]$AdbCommand = "adb"
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$toolRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$scenarios = [ordered]@{
    "1" = @{
        Label = "Allgemeines Geraetelog"
        Script = "device_log_helper.ps1"
    }
    "2" = @{
        Label = "Kaltstart"
        Script = "coldstart_log_helper.ps1"
    }
    "3" = @{
        Label = "OPML-Import"
        Script = "opml_import_log_helper.ps1"
    }
    "4" = @{
        Label = "Reader / WebView"
        Script = "webview_log_helper.ps1"
    }
    "5" = @{
        Label = "Benachrichtigung -> Resume -> Feed"
        Script = "notification_resume_log_helper.ps1"
    }
    "6" = @{
        Label = "Hintergrundaktualisierung"
        Script = "background_refresh_log_helper.ps1"
    }
}

Write-Host ""
Write-Host "RSS Reader Logger ($Mode)"
Write-Host "Bitte den passenden Testfall waehlen:"
Write-Host ""

foreach ($key in $scenarios.Keys) {
    Write-Host ("{0}. {1}" -f $key, $scenarios[$key].Label)
}

Write-Host ""
$selection = Read-Host "Nummer eingeben"

if (-not $scenarios.Contains($selection)) {
    throw "Ungueltige Auswahl: $selection"
}

$selectedScenario = $scenarios[$selection]
$targetScript = Join-Path $toolRoot $selectedScenario.Script

if (-not (Test-Path -LiteralPath $targetScript)) {
    throw "Helper-Skript nicht gefunden: $targetScript"
}

Write-Host ""
Write-Host ("Starte: {0}" -f $selectedScenario.Label)
Write-Host ""

& $targetScript -Mode $Mode -PackageName $PackageName -OutputDir $OutputDir -AdbCommand $AdbCommand

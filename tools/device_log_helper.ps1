param(
    [ValidateSet("prepare", "collect")]
    [string]$Mode = "collect",
    [string]$PackageName = "de.lolo.rssreader",
    [string]$OutputDir = "D:\Codex\RSS_Reader_Android\Ausgabe_APK",
    [string]$AdbCommand = "adb"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $AdbCommand @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB-Aufruf fehlgeschlagen: $AdbCommand $($Arguments -join ' ')"
    }
}

function Get-TimeStamp {
    return Get-Date -Format "yyyyMMdd-HHmmss"
}

function Invoke-RawAdbRedirect {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbArguments,
        [Parameter(Mandatory = $true)]
        [string]$TargetPath
    )

    $escapedTarget = '"' + $TargetPath + '"'
    $commandLine = "$AdbCommand $AdbArguments > $escapedTarget"
    cmd /c $commandLine
    if ($LASTEXITCODE -ne 0) {
        throw "ADB-Redirect fehlgeschlagen: $commandLine"
    }
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$timeStamp = Get-TimeStamp
$crashLogPath = Join-Path $OutputDir "rss-reader-crash-log-$timeStamp.txt"
$debugLogPath = Join-Path $OutputDir "rss-reader-debug-log-$timeStamp.txt"

switch ($Mode) {
    "prepare" {
        Write-Host "Pruefe angeschlossene Geraete..."
        Invoke-Adb -Arguments @("devices")

        Write-Host "Leere logcat..."
        Invoke-Adb -Arguments @("logcat", "-c")

        Write-Host "Loesche App-Debuglog..."
        & $AdbCommand "shell" "run-as" $PackageName "rm" "-f" "files/debug/rss-reader-debug.log" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht geloescht werden. Ist der Debug-Build schon installiert?"
        }

        Write-Host ""
        Write-Host "Vorbereitung abgeschlossen."
        Write-Host "Jetzt App starten und Fehler/Testablauf ausfuehren."
    }

    "collect" {
        Write-Host "Exportiere logcat nach $crashLogPath"
        Invoke-RawAdbRedirect -AdbArguments "logcat -d" -TargetPath $crashLogPath

        Write-Host "Exportiere App-Debuglog nach $debugLogPath"
        cmd /c "$AdbCommand exec-out run-as $PackageName cat files/debug/rss-reader-debug.log > ""$debugLogPath"""
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht gelesen werden. Pruefe, ob ein Debug-Build installiert ist."
        }

        Write-Host ""
        Write-Host "Logs exportiert:"
        Write-Host $crashLogPath
        Write-Host $debugLogPath
    }
}

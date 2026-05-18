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

function Clear-LogcatBuffers {
    Invoke-Adb -Arguments @("logcat", "-b", "main", "-b", "system", "-b", "crash", "-b", "events", "-c")
    Start-Sleep -Milliseconds 250
}

function Write-LogcatMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MarkerPath
    )

    $marker = "RSS_READER_LOG_MARKER $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')"
    Set-Content -Path $MarkerPath -Value $marker -Encoding UTF8

    & $AdbCommand "shell" "log" "-t" "RSSReaderMarker" $marker | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Logcat-Marker konnte nicht geschrieben werden."
    }
}

function Trim-LogFileToMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$LogPath,
        [Parameter(Mandatory = $true)]
        [string]$MarkerPath
    )

    if (-not (Test-Path $LogPath) -or -not (Test-Path $MarkerPath)) {
        return
    }

    $marker = (Get-Content -Path $MarkerPath -TotalCount 1).Trim()
    if (-not $marker) {
        return
    }

    $lines = Get-Content -Path $LogPath
    $markerIndex = -1
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -like "*$marker*") {
            $markerIndex = $i
            break
        }
    }

    if ($markerIndex -ge 0) {
        $lines[$markerIndex..($lines.Length - 1)] | Set-Content -Path $LogPath -Encoding UTF8
    } else {
        Write-Warning "Logcat-Marker wurde im exportierten Log nicht gefunden. Das Log enthaelt moeglicherweise alte Eintraege."
    }

    Remove-Item -Path $MarkerPath -ErrorAction SilentlyContinue
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$timeStamp = Get-TimeStamp
$crashLogPath = Join-Path $OutputDir "rss-reader-opml-import-crash-log-$timeStamp.txt"
$debugLogPath = Join-Path $OutputDir "rss-reader-opml-import-debug-log-$timeStamp.txt"
$focusLogPath = Join-Path $OutputDir "rss-reader-opml-import-focus-log-$timeStamp.txt"
$markerPath = Join-Path $OutputDir "rss-reader-opml-import-log-marker.txt"

switch ($Mode) {
    "prepare" {
        Write-Host "Pruefe angeschlossene Geraete..."
        Invoke-Adb -Arguments @("devices")

        Write-Host "Leere logcat..."
        Clear-LogcatBuffers

        Write-Host "Loesche App-Debuglog..."
        & $AdbCommand "shell" "run-as" $PackageName "rm" "-f" "files/debug/rss-reader-debug.log" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht geloescht werden. Ist der Debug-Build schon installiert?"
        }

        Write-Host "Setze Log-Marker..."
        Write-LogcatMarker -MarkerPath $markerPath

        Write-Host ""
        Write-Host "Vorbereitung fuer OPML-Import abgeschlossen."
        Write-Host "Jetzt die App starten und den OPML-Import ausfuehren."
    }

    "collect" {
        Write-Host "Exportiere logcat nach $crashLogPath"
        Invoke-RawAdbRedirect -AdbArguments "logcat -d" -TargetPath $crashLogPath
        Trim-LogFileToMarker -LogPath $crashLogPath -MarkerPath $markerPath

        Write-Host "Exportiere App-Debuglog nach $debugLogPath"
        cmd /c "$AdbCommand exec-out run-as $PackageName cat files/debug/rss-reader-debug.log > ""$debugLogPath"" 2>nul"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht gelesen werden. Pruefe, ob ein Debug-Build installiert ist."
        }

        Write-Host "Erzeuge fokussierten OPML-Import-Auszug nach $focusLogPath"
        @(
            "===== OPML-Import fokussierter Auszug ====="
            ""
            "---- App-Debuglog: Import-/XML-/Fehlerhinweise ----"
        ) | Set-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $debugLogPath) {
            Select-String -Path $debugLogPath -Pattern "Import|OPML|Xml|xml|Invalid|Unsupported|Exception|Fehler|Warn|Error" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- logcat: Package-/DocumentsUI-/Storage-/XML-Hinweise ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $crashLogPath) {
            Select-String -Path $crashLogPath -Pattern "de.lolo.rssreader|DocumentsUI|documentui|Storage|Xml|xml|Parser|DOCTYPE|Entity|Invalid|Unsupported|Exception|AndroidRuntime|FATAL EXCEPTION" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        Write-Host ""
        Write-Host "OPML-Import-Logs exportiert:"
        Write-Host $crashLogPath
        Write-Host $debugLogPath
        Write-Host $focusLogPath
    }
}

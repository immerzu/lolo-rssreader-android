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
$crashLogPath = Join-Path $OutputDir "rss-reader-crash-log-$timeStamp.txt"
$debugLogPath = Join-Path $OutputDir "rss-reader-debug-log-$timeStamp.txt"
$focusLogPath = Join-Path $OutputDir "rss-reader-focus-log-$timeStamp.txt"
$stateLogPath = Join-Path $OutputDir "rss-reader-state-log-$timeStamp.txt"
$uiStateLogPath = Join-Path $OutputDir "rss-reader-ui-state-log-$timeStamp.txt"
$markerPath = Join-Path $OutputDir "rss-reader-device-log-marker.txt"

switch ($Mode) {
    "prepare" {
        Write-Host "Pruefe angeschlossene Geraete..."
        Invoke-Adb -Arguments @("devices")

        Write-Host "Leere logcat..."
        Clear-LogcatBuffers

        Write-Host "Loesche App-Debuglog..."
        try {
            & $AdbCommand "shell" "run-as" $PackageName "rm" "-f" "files/debug/rss-reader-debug.log" 2>&1 | Out-Null
        } catch {
            # ignore
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht geloescht werden (run-as fehlgeschlagen). Das ist bei manchen Geraeten normal. Der Test kann trotzdem fortgesetzt werden."
        }

        Write-Host "Setze Log-Marker..."
        Write-LogcatMarker -MarkerPath $markerPath

        Write-Host ""
        Write-Host "Vorbereitung abgeschlossen."
        Write-Host "Jetzt App starten und Fehler/Testablauf ausfuehren."
    }

    "collect" {
        Write-Host "Exportiere logcat nach $crashLogPath"
        Invoke-RawAdbRedirect -AdbArguments "logcat -d -b main -b system -b crash -b events -v threadtime" -TargetPath $crashLogPath
        Trim-LogFileToMarker -LogPath $crashLogPath -MarkerPath $markerPath

        Write-Host "Exportiere App-Debuglog nach $debugLogPath"
        try {
            cmd /c "$AdbCommand exec-out run-as $PackageName cat files/debug/rss-reader-debug.log > ""$debugLogPath"" 2>nul"
        } catch {
            # ignore
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht gelesen werden (run-as fehlgeschlagen). Das ist bei manchen Geraeten normal."
        }

        Write-Host "Exportiere zusaetzlichen Systemzustand nach $stateLogPath"
        @(
            "===== RSS Reader Zustand ====="
            "Zeitstempel: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
            ""
            "---- dumpsys package $PackageName ----"
        ) | Set-Content -Path $stateLogPath -Encoding UTF8

        (& $AdbCommand "shell" "dumpsys" "package" $PackageName) | Add-Content -Path $stateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys meminfo $PackageName ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "meminfo" $PackageName) | Add-Content -Path $stateLogPath -Encoding UTF8
        @(
            ""
            "---- cmd webviewupdate getCurrentWebViewPackage ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "cmd" "webviewupdate" "getCurrentWebViewPackage") | Add-Content -Path $stateLogPath -Encoding UTF8
        @(
            ""
            "---- pidof $PackageName ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "pidof" $PackageName) | Add-Content -Path $stateLogPath -Encoding UTF8

        Write-Host "Exportiere UI-/Fenster-/Activity-Zustand nach $uiStateLogPath"
        @(
            "===== RSS Reader UI-/Activity-Zustand ====="
            "Zeitstempel: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
            ""
            "---- dumpsys activity top ----"
        ) | Set-Content -Path $uiStateLogPath -Encoding UTF8

        (& $AdbCommand "shell" "dumpsys" "activity" "top") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys activity activities ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "activity" "activities") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys activity processes ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "activity" "processes") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys window windows ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "window" "windows") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys input ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "input") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys gfxinfo $PackageName ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "gfxinfo" $PackageName) | Add-Content -Path $uiStateLogPath -Encoding UTF8

        Write-Host "Erzeuge fokussierten Analyse-Auszug nach $focusLogPath"
        @(
            "===== RSS Reader fokussierter Auszug ====="
            "Analyse-Set:"
            "  crash-log: $crashLogPath"
            "  debug-log: $debugLogPath"
            "  focus-log: $focusLogPath"
            "  state-log: $stateLogPath"
            "  ui-state-log: $uiStateLogPath"
            ""
            "---- App-Debuglog: App-/Reader-/Import-/Refresh-/Fehlerhinweise ----"
        ) | Set-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $debugLogPath) {
            Select-String -Path $debugLogPath -Pattern "App:|FeedRepository|FeedFetcher|FeedParser|ArticleListScreen|ArticleReaderScreen|WebView|Import|refreshAll|FTS|Fehler|Warn|Exception|Browser|Seite geladen|Inhalt geladen|about:blank" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- logcat: App-/Crash-/WebView-/Chromium-/Render-Hinweise ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $crashLogPath) {
            Select-String -Path $crashLogPath -Pattern "de.lolo.rssreader|AndroidRuntime|FATAL EXCEPTION|Fatal signal|SIG|WebView|chromium|cr_WebView|AwContents|Renderer|render|tile memory|about:blank|DocumentsUI|ActivityTaskManager|WindowManager|SurfaceFlinger|BLASTBufferQueue|BufferQueue|HWUI|SQLite|Room|IllegalStateException|NullPointerException|OutOfMemory|ANR|Input dispatching timed out|HTTP" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- UI-Zustand: Activity-/Fenster-/Render-Hinweise ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $uiStateLogPath) {
            Select-String -Path $uiStateLogPath -Pattern "de.lolo.rssreader|MainActivity|mCurrentFocus|mFocusedApp|ResumedActivity|topResumedActivity|Window|Task|ActivityRecord|ProcessRecord|oom|foreground|visible|gfxinfo|Janky|Frame|Draw|Render|Buffer" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        Write-Host ""
        Write-Host "Logs exportiert. Fuer gruendliche Analyse bitte immer alle fuenf Dateien gemeinsam verwenden:"
        Write-Host $crashLogPath
        Write-Host $debugLogPath
        Write-Host $focusLogPath
        Write-Host $stateLogPath
        Write-Host $uiStateLogPath
    }
}

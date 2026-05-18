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
$crashLogPath = Join-Path $OutputDir "rss-reader-notification-resume-crash-log-$timeStamp.txt"
$debugLogPath = Join-Path $OutputDir "rss-reader-notification-resume-debug-log-$timeStamp.txt"
$focusLogPath = Join-Path $OutputDir "rss-reader-notification-resume-focus-log-$timeStamp.txt"
$stateLogPath = Join-Path $OutputDir "rss-reader-notification-resume-state-log-$timeStamp.txt"
$uiStateLogPath = Join-Path $OutputDir "rss-reader-notification-resume-ui-state-log-$timeStamp.txt"
$markerPath = Join-Path $OutputDir "rss-reader-notification-resume-log-marker.txt"

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
        Write-Host "Vorbereitung fuer Notification-/Resume-Test abgeschlossen."
        Write-Host "Jetzt bitte genau diesen Ablauf ausfuehren:"
        Write-Host "1. App in den Hintergrund / Ruhemodus gehen lassen"
        Write-Host "2. Auf Android-Benachrichtigung tippen"
        Write-Host "3. In der Uebersicht einen Feed oeffnen"
    }

    "collect" {
        Write-Host "Exportiere logcat nach $crashLogPath"
        Invoke-RawAdbRedirect -AdbArguments "logcat -d -b main -b system -b crash -b events -v threadtime" -TargetPath $crashLogPath
        Trim-LogFileToMarker -LogPath $crashLogPath -MarkerPath $markerPath

        Write-Host "Exportiere App-Debuglog nach $debugLogPath"
        cmd /c "$AdbCommand exec-out run-as $PackageName cat files/debug/rss-reader-debug.log > ""$debugLogPath"" 2>nul"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "App-Debuglog konnte nicht gelesen werden. Pruefe, ob ein Debug-Build installiert ist."
        }

        Write-Host "Exportiere zusaetzlichen Systemzustand nach $stateLogPath"
        @(
            "===== RSS Reader Notification-/Resume-Zustand ====="
            "Zeitstempel: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
            ""
            "---- dumpsys package $PackageName ----"
        ) | Set-Content -Path $stateLogPath -Encoding UTF8

        (& $AdbCommand "shell" "dumpsys" "package" $PackageName) | Add-Content -Path $stateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys notification --noredact ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "notification" "--noredact") | Add-Content -Path $stateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys activity activities ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "activity" "activities") | Add-Content -Path $stateLogPath -Encoding UTF8

        Write-Host "Exportiere UI-/Fenster-/Activity-Zustand nach $uiStateLogPath"
        @(
            "===== RSS Reader Notification-/Resume-UI-Zustand ====="
            "Zeitstempel: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
            ""
            "---- dumpsys activity top ----"
        ) | Set-Content -Path $uiStateLogPath -Encoding UTF8

        (& $AdbCommand "shell" "dumpsys" "activity" "top") | Add-Content -Path $uiStateLogPath -Encoding UTF8
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

        Write-Host "Erzeuge fokussierten Analyse-Auszug nach $focusLogPath"
        @(
            "===== RSS Reader Notification-/Resume-Auszug ====="
            "Analyse-Set:"
            "  crash-log: $crashLogPath"
            "  debug-log: $debugLogPath"
            "  focus-log: $focusLogPath"
            "  state-log: $stateLogPath"
            "  ui-state-log: $uiStateLogPath"
            ""
            "---- App-Debuglog: Notification / Intent / Route / Feed-Klick ----"
        ) | Set-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $debugLogPath) {
            Select-String -Path $debugLogPath -Pattern "ArticleUpdateNotifier|MainActivity|onCreate|onNewIntent|RssReaderApp|App im Vordergrund|App im Hintergrund|Route aktiv|FeedListScreen|Feed aus Uebersicht angeklickt|Feed-Menue per Long-Click|ArticleListScreen|sichtbar: feedId|Artikel aus Liste geoeffnet" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- logcat: Notification / Activity / Window / Process ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $crashLogPath) {
            Select-String -Path $crashLogPath -Pattern "de.lolo.rssreader|rss_updates|Notification|PendingIntent|ActivityTaskManager|WindowManager|am_proc_start|am_proc_bound|wm_on_resume_called|wm_on_pause_called|wm_on_stop_called|wm_resume_activity|wm_pause_activity|mCurrentFocus|mFocusedApp|InputDispatcher|AndroidRuntime|FATAL EXCEPTION|ANR" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- UI-Zustand: sichtbares Fenster / Focus ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $uiStateLogPath) {
            Select-String -Path $uiStateLogPath -Pattern "de.lolo.rssreader|MainActivity|mCurrentFocus|mFocusedApp|ResumedActivity|topResumedActivity|hasFocus|visible|Window|Input" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        Write-Host ""
        Write-Host "Notification-/Resume-Logs exportiert. Bitte immer alle fuenf Dateien gemeinsam verwenden:"
        Write-Host $crashLogPath
        Write-Host $debugLogPath
        Write-Host $focusLogPath
        Write-Host $stateLogPath
        Write-Host $uiStateLogPath
    }
}

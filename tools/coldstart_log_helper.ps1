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

function Get-LogcatDebugFallback {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CrashLogPath
    )

    if (-not (Test-Path $CrashLogPath)) {
        return @()
    }

    $matches = Select-String -Path $CrashLogPath -Pattern "RSSReaderDebug|Application gestartet|MainActivity|HomeScreen|FeedListScreen|feedsLoaded|feedsLoadedForUi|leer gerendert" -CaseSensitive:$false
    return $matches | ForEach-Object { $_.Line }
}

function Export-AppDebugLog {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DebugLogPath,
        [Parameter(Mandatory = $true)]
        [string]$CrashLogPath,
        [Parameter(Mandatory = $true)]
        [string]$PackageName
    )

    try {
        $fileExists = & $AdbCommand "shell" "run-as" $PackageName "test" "-f" "files/debug/rss-reader-debug.log" 2>&1
        $existsExitCode = $LASTEXITCODE
    } catch {
        $fileExists = @($_.Exception.Message)
        $existsExitCode = 1
    }
    $primaryOutput = @()
    $primaryExitCode = $existsExitCode

    if ($existsExitCode -eq 0) {
        try {
            $primaryOutput = & $AdbCommand "shell" "run-as" $PackageName "head" "-n" "400" "files/debug/rss-reader-debug.log" 2>&1
            $primaryExitCode = $LASTEXITCODE
        } catch {
            $primaryOutput = @($_.Exception.Message)
            $primaryExitCode = 1
        }
    }

    if ($primaryExitCode -eq 0 -and $primaryOutput -and ($primaryOutput -join "`n").Trim().Length -gt 0) {
        $primaryOutput | Set-Content -Path $DebugLogPath -Encoding UTF8
        return
    }

    @(
        "===== RSS Reader Kaltstart Debuglog ====="
        "Primärer Export fehlgeschlagen oder war leer."
        "Zeitstempel: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        "ADB-ExitCode: $primaryExitCode"
        "Datei vorhanden: $($existsExitCode -eq 0)"
        ""
        "---- Primäre Fehl-/Statusausgabe ----"
    ) | Set-Content -Path $DebugLogPath -Encoding UTF8

    if ($primaryOutput) {
        $primaryOutput | Add-Content -Path $DebugLogPath -Encoding UTF8
    } else {
        "(keine Ausgabe)" | Add-Content -Path $DebugLogPath -Encoding UTF8
    }

    @(
        ""
        "---- App-Dateisystem-Diagnose via run-as ----"
    ) | Add-Content -Path $DebugLogPath -Encoding UTF8

    try {
        $fsDiag = & $AdbCommand "shell" "run-as" $PackageName "sh" "-c" "pwd; echo '---'; ls -la; echo '---'; ls -la files 2>/dev/null; echo '---'; ls -la files/debug 2>/dev/null; echo '---'; [ -f files/debug/rss-reader-debug.log ] && wc -c files/debug/rss-reader-debug.log || echo 'debuglog-missing'" 2>&1
    } catch {
        $fsDiag = @($_.Exception.Message)
    }
    if ($fsDiag) {
        $fsDiag | Add-Content -Path $DebugLogPath -Encoding UTF8
    } else {
        "(keine Dateisystem-Diagnose verfuegbar)" | Add-Content -Path $DebugLogPath -Encoding UTF8
    }

    @(
        ""
        "---- Fallback aus logcat / RSSReaderDebug ----"
    ) | Add-Content -Path $DebugLogPath -Encoding UTF8

    $fallbackLines = Get-LogcatDebugFallback -CrashLogPath $CrashLogPath
    if ($fallbackLines.Count -gt 0) {
        $fallbackLines | Add-Content -Path $DebugLogPath -Encoding UTF8
    } else {
        "(keine passenden RSSReaderDebug-/Startzeilen im logcat gefunden)" | Add-Content -Path $DebugLogPath -Encoding UTF8
    }
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$timeStamp = Get-TimeStamp
$crashLogPath = Join-Path $OutputDir "rss-reader-coldstart-crash-log-$timeStamp.txt"
$debugLogPath = Join-Path $OutputDir "rss-reader-coldstart-debug-log-$timeStamp.txt"
$focusLogPath = Join-Path $OutputDir "rss-reader-coldstart-focus-log-$timeStamp.txt"
$stateLogPath = Join-Path $OutputDir "rss-reader-coldstart-state-log-$timeStamp.txt"
$uiStateLogPath = Join-Path $OutputDir "rss-reader-coldstart-ui-state-log-$timeStamp.txt"
$markerPath = Join-Path $OutputDir "rss-reader-coldstart-log-marker.txt"

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

        Write-Host "Beende App-Prozess fuer echten Kaltstart..."
        & $AdbCommand "shell" "am" "force-stop" $PackageName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "force-stop konnte nicht bestaetigt werden."
        }

        Write-Host "Gehe auf den Home-Screen..."
        & $AdbCommand "shell" "input" "keyevent" "3" | Out-Null

        Write-Host "Setze Log-Marker..."
        Write-LogcatMarker -MarkerPath $markerPath

        Write-Host ""
        Write-Host "Vorbereitung fuer Kaltstart-Test abgeschlossen."
        Write-Host "Jetzt die App genau einmal frisch starten und nur den Kaltstart beobachten."
    }

    "collect" {
        Write-Host "Exportiere logcat nach $crashLogPath"
        Invoke-RawAdbRedirect -AdbArguments "logcat -d -b main -b system -b crash -b events -v threadtime" -TargetPath $crashLogPath
        Trim-LogFileToMarker -LogPath $crashLogPath -MarkerPath $markerPath

        Write-Host "Exportiere App-Debuglog nach $debugLogPath"
        Export-AppDebugLog -DebugLogPath $debugLogPath -CrashLogPath $crashLogPath -PackageName $PackageName

        Write-Host "Exportiere zusaetzlichen App-Zustand nach $stateLogPath"
        @(
            "===== RSS Reader Kaltstart-Zustand ====="
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
            "---- pidof $PackageName ----"
        ) | Add-Content -Path $stateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "pidof" $PackageName) | Add-Content -Path $stateLogPath -Encoding UTF8

        Write-Host "Exportiere UI-/Fenster-/Activity-Zustand nach $uiStateLogPath"
        @(
            "===== RSS Reader Kaltstart UI-/Activity-Zustand ====="
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
            "---- dumpsys window windows ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "window" "windows") | Add-Content -Path $uiStateLogPath -Encoding UTF8
        @(
            ""
            "---- dumpsys gfxinfo $PackageName ----"
        ) | Add-Content -Path $uiStateLogPath -Encoding UTF8
        (& $AdbCommand "shell" "dumpsys" "gfxinfo" $PackageName) | Add-Content -Path $uiStateLogPath -Encoding UTF8

        Write-Host "Erzeuge fokussierten Kaltstart-Auszug nach $focusLogPath"
        @(
            "===== RSS Reader Kaltstart fokussierter Auszug ====="
            "Analyse-Set:"
            "  crash-log: $crashLogPath"
            "  debug-log: $debugLogPath"
            "  focus-log: $focusLogPath"
            "  state-log: $stateLogPath"
            "  ui-state-log: $uiStateLogPath"
            ""
            "---- App-Debuglog: Start-/Home-/Feedlisten-Timeline ----"
        ) | Set-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $debugLogPath) {
            Select-String -Path $debugLogPath -Pattern "Application gestartet|MainActivity|HomeScreen|FeedListScreen|sichtbar|verlassen|feedsLoaded|feedsLoadedForUi|feedCount|leer gerendert|refreshAll|Import|onCreate|onStart|onResume|onPause|onStop|onWindowFocusChanged" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- logcat: Prozess-/Activity-/Window-/Start-Hinweise ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $crashLogPath) {
            Select-String -Path $crashLogPath -Pattern "de.lolo.rssreader|am_proc_start|am_proc_bound|am_kill|wm_on_create_called|wm_on_start_called|wm_on_resume_called|wm_on_pause_called|wm_on_stop_called|wm_on_top_resumed_gained_called|wm_on_top_resumed_lost_called|wm_create_activity|wm_resume_activity|wm_pause_activity|wm_stop_activity|wm_add_to_stopping|ActivityTaskManager|WindowManager|mCurrentFocus|mFocusedApp|Displayed|launching|LaunchState|SplashScreen|AndroidRuntime|FATAL EXCEPTION|ANR|Input dispatching timed out" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        @(
            ""
            "---- UI-Zustand: sichtbares Fenster / Focus / Render ----"
        ) | Add-Content -Path $focusLogPath -Encoding UTF8

        if (Test-Path $uiStateLogPath) {
            Select-String -Path $uiStateLogPath -Pattern "de.lolo.rssreader|MainActivity|mWindowVisibility|mHasWindowFocus|mHasSurface|isReadyForDisplay|ViewRootImpl|mCurrentFocus|mFocusedApp|ResumedActivity|topResumedActivity|visible|surface|Draw|Frame|Janky" -CaseSensitive:$false |
                ForEach-Object { $_.Line } |
                Add-Content -Path $focusLogPath -Encoding UTF8
        }

        Write-Host ""
        Write-Host "Kaltstart-Logs exportiert. Fuer diese Analyse bitte immer alle fuenf Dateien gemeinsam verwenden:"
        Write-Host $crashLogPath
        Write-Host $debugLogPath
        Write-Host $focusLogPath
        Write-Host $stateLogPath
        Write-Host $uiStateLogPath
    }
}

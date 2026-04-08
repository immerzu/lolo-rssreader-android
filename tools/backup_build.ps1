$apkPath = $args[0]
$sourcePath = $args[1]
$backupRoot = if ($args.Count -ge 3) { $args[2] } else { "D:\Codex\RSS_Reader_Android\Ausgabe_APK\Builds_Sicherungen" }
$keepCount = if ($args.Count -ge 4) { [int]$args[3] } else { 10 }

if ([string]::IsNullOrWhiteSpace($apkPath) -or [string]::IsNullOrWhiteSpace($sourcePath)) {
    throw "Usage: backup_build.ps1 <apkPath> <sourcePath> [backupRoot] [keepCount]"
}

if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "APK not found: $apkPath"
}

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Source snapshot not found: $sourcePath"
}

$apkFile = Get-Item -LiteralPath $apkPath
$sourceFile = Get-Item -LiteralPath $sourcePath

$buildName = [System.IO.Path]::GetFileNameWithoutExtension($apkFile.Name)
$version = "unknown"
$buildTimestamp = $apkFile.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")

if ($buildName -match '^RSS-Reader-v(?<version>.+)-(?<kind>debug|release)(-(?<stamp>\d{8}-\d{6}))?$') {
    $version = $Matches['version']
    if (-not [string]::IsNullOrWhiteSpace($Matches['stamp'])) {
        $buildTimestamp = [datetime]::ParseExact(
            $Matches['stamp'],
            'yyyyMMdd-HHmmss',
            [System.Globalization.CultureInfo]::InvariantCulture
        ).ToString("yyyy-MM-dd HH:mm:ss")
    }
}

New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

$targetDir = Join-Path $backupRoot $buildName
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$targetApk = Join-Path $targetDir $apkFile.Name
$targetSource = Join-Path $targetDir $sourceFile.Name
$infoFile = Join-Path $targetDir "Build-Info.txt"

Copy-Item -LiteralPath $apkFile.FullName -Destination $targetApk -Force
Copy-Item -LiteralPath $sourceFile.FullName -Destination $targetSource -Force

$info = @(
    "BuildName: $buildName"
    "Version: $version"
    "BuildTimestamp: $buildTimestamp"
    "BackedUpAt: $([datetime]::Now.ToString('yyyy-MM-dd HH:mm:ss'))"
    "ApkFile: $($apkFile.Name)"
    "SourceSnapshot: $($sourceFile.Name)"
    "SourceApkPath: $($apkFile.FullName)"
    "SourceSnapshotPath: $($sourceFile.FullName)"
) -join "`r`n"

[System.IO.File]::WriteAllText($infoFile, $info, [System.Text.Encoding]::UTF8)

$backupDirs = Get-ChildItem -LiteralPath $backupRoot -Directory |
    Where-Object {
        $_.Name -like 'RSS-Reader-v*-debug-*' -or
        $_.Name -like 'RSS-Reader-v*-release-*' -or
        $_.Name -like 'RSS-Reader-v*-release'
    } |
    Sort-Object Name -Descending

if ($backupDirs.Count -gt $keepCount) {
    $backupDirs | Select-Object -Skip $keepCount | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Recurse -Force
    }
}

Write-Output $targetDir

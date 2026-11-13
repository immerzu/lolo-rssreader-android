param(
    [Parameter(Position = 0)][string]$apkPath,
    [Parameter(Position = 1)][string]$sourcePath,
    [Parameter(Position = 2)][string]$backupRoot = "F:\001_Coding_Projekte\RSS_Reader_Android\Ausgabe_APK\Builds_Sicherungen",
    [Parameter(Position = 3)][int]$keepCount = 10,
    # Aufraeumen ist eine ausdrueckliche, separate Aktion (Audit RSS-A05):
    # der regulaere Backup-Lauf loescht nichts.
    [switch]$Prune
)

if ([string]::IsNullOrWhiteSpace($apkPath) -or [string]::IsNullOrWhiteSpace($sourcePath)) {
    throw "Usage: backup_build.ps1 <apkPath> <sourcePath> [backupRoot] [keepCount] [-Prune]"
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
if (Test-Path -LiteralPath $targetDir) {
    # Kein stilles Ueberschreiben: eine vorhandene Sicherung bleibt unangetastet.
    throw "Backup target already exists: $targetDir (nothing was overwritten)"
}
New-Item -ItemType Directory -Path $targetDir | Out-Null

$targetApk = Join-Path $targetDir $apkFile.Name
$targetSource = Join-Path $targetDir $sourceFile.Name
$infoFile = Join-Path $targetDir "Build-Info.txt"

Copy-Item -LiteralPath $apkFile.FullName -Destination $targetApk
Copy-Item -LiteralPath $sourceFile.FullName -Destination $targetSource

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

if ($Prune) {
    # Versionsbewusste Sortierung statt lexikografischer Namenssortierung:
    # "1.87.99" darf nicht vor "1.87.100" einsortiert werden (Audit RSS-A05).
    $backupDirs = Get-ChildItem -LiteralPath $backupRoot -Directory |
        Where-Object {
            $_.Name -like 'RSS-Reader-v*-debug-*' -or
            $_.Name -like 'RSS-Reader-v*-release-*' -or
            $_.Name -like 'RSS-Reader-v*-release'
        } |
        Sort-Object -Property @(
            @{ Expression = {
                if ($_.Name -match '^RSS-Reader-v(\d+\.\d+\.\d+)') {
                    [version]$Matches[1]
                } else {
                    [version]'0.0.0'
                }
            } },
            @{ Expression = { $_.LastWriteTime } }
        ) -Descending

    if ($backupDirs.Count -gt $keepCount) {
        $backupDirs | Select-Object -Skip $keepCount | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }
}

Write-Output $targetDir

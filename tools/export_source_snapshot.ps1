param(
    [Parameter(Mandatory=$true)]
    [string]$project,
    [Parameter(Mandatory=$true)]
    [string]$output,
    [string]$mode = "compact"
)

$excludeDirs = @('build', '.gradle', '.idea', '.git', 'signing', 'Ausgabe_APK', '!Backups', 'Release_Versionen', 'old_Versionen', '.android')
$excludeFiles = @('local.properties', 'keystore.properties', 'analytics.settings', 'debug.keystore', 'debug.keystore.lock', 'adbkey', 'adbkey.pub')
$binaryExt = @(
    '.apk', '.aab', '.apks', '.idsig', '.jar', '.keystore', '.jks', '.p12', '.pem', '.key', '.png', '.jpg', '.jpeg', '.webp',
    '.gif', '.bmp', '.ico', '.so', '.ttf', '.otf', '.mp3', '.mp4', '.wav', '.pdf', '.class',
    '.dex', '.db', '.kotlin_module', '.aar', '.log', '.tmp', '.bak'
)

$sensitiveContentPatterns = @(
    'storePassword\s*=',
    'keyPassword\s*=',
    'PRIVATE\s+KEY',
    'BEGIN\s+RSA\s+PRIVATE\s+KEY',
    'BEGIN\s+EC\s+PRIVATE\s+KEY',
    'BEGIN\s+DSA\s+PRIVATE\s+KEY',
    'BEGIN\s+OPENSSH\s+PRIVATE\s+KEY'
)
$propertyLookupPattern = '\.getProperty\s*\(|Properties\s*\(\s*\)'

$rootIncludeFiles = @(
    'build.gradle.kts',
    'settings.gradle.kts',
    'gradle.properties',
    'gradle\libs.versions.toml',
    'version.properties',
    'README.md',
    'LICENSE'
)

$includeLeafFiles = @(
    'build.gradle',
    'build.gradle.kts',
    'proguard-rules.pro',
    'consumer-rules.pro',
    'lint.xml'
)

$includeDirs = @(
    'app\src\main',
    'app\src\test',
    'app\src\androidTest',
    'app\schemas',
    'fastlane\metadata\android',
    'docs',
    'gradle\wrapper',
    'tools'
)

function Test-IsIncludedFile($file) {
    $relative = $file.FullName.Substring($project.Length + 1)
    if ($rootIncludeFiles -contains $relative) {
        return $true
    }
    if ($includeLeafFiles -contains $file.Name) {
        return $true
    }
    foreach ($dir in $includeDirs) {
        if ($relative.StartsWith($dir + '\') -or $relative -eq $dir) {
            return $true
        }
    }
    return $false
}

# Die vorhandene Ausgabe wird NICHT vorab geloescht: bricht die Secret-Pruefung ab,
# bleibt der letzte gueltige Stand erhalten (Audit RSS-A25). Geschrieben wird erst
# nach vollstaendig erfolgreicher Pruefung.
$builder = [System.Text.StringBuilder]::new()

Get-ChildItem $project -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
        $f = $_.FullName
        $dirExcluded = $false
        foreach ($dir in $excludeDirs) {
            $sep = [System.IO.Path]::DirectorySeparatorChar
            if ($f -like "*$sep$dir$sep*") {
                $dirExcluded = $true
                break
            }
        }
        $passesExcludes =
            -not $dirExcluded -and
            $excludeFiles -notcontains $_.Name -and
            $binaryExt -notcontains $_.Extension.ToLowerInvariant()

        if (-not $passesExcludes) {
            return $false
        }

        if ($mode -eq 'full') {
            return $true
        }

        return Test-IsIncludedFile $_
    } |
    Sort-Object FullName |
    ForEach-Object {
        $relPath = $_.FullName.Substring($project.Length + 1).Replace('\', '/')
        $fileContent = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
        if ($fileContent) {
            $sensitiveHit = $sensitiveContentPatterns | Where-Object { $fileContent -match $_ }
            if ($sensitiveHit) {
                $isExampleFile = $_.Name.EndsWith('.example', [StringComparison]::OrdinalIgnoreCase)
                $isPlaceholderOnly = ($fileContent -match 'storePassword\s*=\s*CHANGE_ME') -or
                                     ($fileContent -match 'keyPassword\s*=\s*CHANGE_ME') -or
                                     ($fileContent -match 'keyAlias\s*=\s*CHANGE_ME')
                $isPropertyLookup = $fileContent -match $propertyLookupPattern
                # Ein Property-Lookup an einer Stelle befreit die Datei NICHT von der
                # Pruefung: hart codierte Passwoerter bleiben ein Abbruchgrund
                # (Audit RSS-A25).
                $hasHardcodedPassword =
                    $fileContent -match '(storePassword|keyPassword)\s*=\s*["''][^"'']+["'']'
                if ($hasHardcodedPassword -and -not $isPlaceholderOnly) {
                    Write-Error "SICHERHEITSABBRUCH: '$($_.FullName)' enthaelt ein hart codiertes Passwort. Export abgebrochen."
                    exit 1
                }
                $isPropertyLookupFile =
                    $isPropertyLookup -and
                    ($_.Extension -in @('.kts', '.gradle', '.properties', '.toml') -or
                        $_.Name.EndsWith('.example', [StringComparison]::OrdinalIgnoreCase))
                if (($isExampleFile -and $isPlaceholderOnly) -or $isPropertyLookupFile) {
                    Write-Host "HINWEIS: '$($_.Name)' enthaelt Signing-Begriffe, aber nur als Property-Lookup oder Platzhalter - wird exportiert."
                } else {
                    Write-Error "SICHERHEITSABBRUCH: '$($_.FullName)' enthaelt echte Secrets (Muster: $sensitiveHit). Export abgebrochen."
                    exit 1
                }
            }
        }
        [void]$builder.AppendLine('=' * 100)
        [void]$builder.AppendLine("DATEI: $relPath")
        [void]$builder.AppendLine('=' * 100)
        [void]$builder.AppendLine()
        $lines = $fileContent -split "`r`n"
        foreach ($line in $lines) {
            [void]$builder.AppendLine($line)
        }
        [void]$builder.AppendLine()
        [void]$builder.AppendLine()
    }

[System.IO.File]::WriteAllText($output, $builder.ToString(), [System.Text.Encoding]::UTF8)

Write-Output $output

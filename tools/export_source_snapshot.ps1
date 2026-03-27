$project = $args[0]
$output = $args[1]
$mode = if ($args.Count -ge 3) { $args[2] } else { "compact" }

$excludeDirs = @('build', '.gradle', '.idea', '.git')
$excludeFiles = @('local.properties', 'keystore.properties')
$binaryExt = @(
    '.apk', '.aab', '.jar', '.keystore', '.jks', '.p12', '.png', '.jpg', '.jpeg', '.webp',
    '.gif', '.bmp', '.ico', '.so', '.ttf', '.otf', '.mp3', '.mp4', '.wav', '.pdf', '.class',
    '.dex', '.db', '.kotlin_module', '.aar'
)

$rootIncludeFiles = @(
    'build.gradle.kts',
    'settings.gradle.kts',
    'gradle.properties',
    'version.properties',
    'README.md',
    'LICENSE'
)

$includeDirs = @(
    'app\src\main',
    'app\src\test',
    'gradle\wrapper',
    'tools'
)

function Test-IsIncludedFile($file) {
    $relative = $file.FullName.Substring($project.Length + 1)
    if ($rootIncludeFiles -contains $relative) {
        return $true
    }

    foreach ($dir in $includeDirs) {
        if ($relative.StartsWith($dir + '\') -or $relative -eq $dir) {
            return $true
        }
    }

    return $false
}

if (Test-Path $output) {
    Remove-Item $output -Force
}

$builder = [System.Text.StringBuilder]::new()

Get-ChildItem $project -Recurse -File |
    Where-Object {
        $full = $_.FullName
        $name = $_.Name
        $ext = $_.Extension.ToLowerInvariant()
        $passesExcludes =
            -not ($excludeDirs | Where-Object { $full -like "*\\$_\\*" }) -and
            $excludeFiles -notcontains $name -and
            $binaryExt -notcontains $ext

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
        [void]$builder.AppendLine('=' * 100)
        [void]$builder.AppendLine("DATEI: " + $_.FullName.Substring($project.Length + 1).Replace('\', '/'))
        [void]$builder.AppendLine('=' * 100)
        [void]$builder.AppendLine()
        foreach ($line in (Get-Content $_.FullName -ErrorAction SilentlyContinue)) {
            [void]$builder.AppendLine($line)
        }
        [void]$builder.AppendLine()
        [void]$builder.AppendLine()
    }

[System.IO.File]::WriteAllText($output, $builder.ToString(), [System.Text.Encoding]::UTF8)

Write-Output $output

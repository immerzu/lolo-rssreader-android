$project = $args[0]
$output = $args[1]

$excludeDirs = @('build', '.gradle', '.idea', '.git')
$excludeFiles = @('local.properties', 'keystore.properties')
$binaryExt = @(
    '.apk', '.aab', '.jar', '.keystore', '.jks', '.p12', '.png', '.jpg', '.jpeg', '.webp',
    '.gif', '.bmp', '.ico', '.so', '.ttf', '.otf', '.mp3', '.mp4', '.wav', '.pdf', '.class',
    '.dex', '.db', '.kotlin_module', '.aar'
)

if (Test-Path $output) {
    Remove-Item $output -Force
}

Get-ChildItem $project -Recurse -File |
    Where-Object {
        $full = $_.FullName
        $name = $_.Name
        $ext = $_.Extension.ToLowerInvariant()
        -not ($excludeDirs | Where-Object { $full -like "*\\$_\\*" }) -and
            $excludeFiles -notcontains $name -and
            $binaryExt -notcontains $ext
    } |
    Sort-Object FullName |
    ForEach-Object {
        Add-Content -Path $output -Value ('=' * 100)
        Add-Content -Path $output -Value ("DATEI: " + $_.FullName.Substring($project.Length + 1).Replace('\', '/'))
        Add-Content -Path $output -Value ('=' * 100)
        Add-Content -Path $output -Value ''
        Get-Content $_.FullName -ErrorAction SilentlyContinue | Add-Content -Path $output
        Add-Content -Path $output -Value "`r`n"
    }

Write-Output $output

$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$javaHome = (Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$studioCandidates = @(
    "C:\Program Files\Android\Android Studio\bin\studio64.exe",
    (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\bin\studio64.exe")
)
$studioPath = $studioCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $javaHome) {
    throw "JDK 17 was not found."
}

if (-not $studioPath) {
    throw "Android Studio was not found."
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true"
$env:Path = @(
    (Join-Path $javaHome "bin"),
    (Join-Path $sdkRoot "platform-tools"),
    (Join-Path $sdkRoot "emulator"),
    $env:Path
) -join ";"

Start-Process -FilePath $studioPath -WorkingDirectory $projectDir -ArgumentList $projectDir

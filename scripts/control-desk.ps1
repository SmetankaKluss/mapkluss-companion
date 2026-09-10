param(
    [ValidateSet('1.21.11', '26.2')]
    [string]$MinecraftVersion = '1.21.11',
    [ValidateSet('empty', 'loading', 'populated', 'error', 'long-name')]
    [string]$Fixture = 'populated',
    [ValidateSet('ru', 'en')]
    [string]$Language = 'ru',
    [switch]$Debug
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$javaVersion = if ($MinecraftVersion -eq '26.2') { 25 } else { 21 }

function Find-JetBrainsRuntime([int]$requiredVersion) {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($env:MAPKLUSS_CONTROL_DESK_JAVA_HOME) { $candidates.Add($env:MAPKLUSS_CONTROL_DESK_JAVA_HOME) }
    $roots = @((Join-Path $env:USERPROFILE '.gradle\jdks'), (Join-Path $env:LOCALAPPDATA 'JetBrains'), (Join-Path $env:LOCALAPPDATA 'Programs'), 'C:\Program Files\JetBrains')
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Directory -Recurse -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidateHome in $candidates | Select-Object -Unique) {
        $release = Join-Path $candidateHome 'release'
        if (-not (Test-Path -LiteralPath $release)) { continue }
        $contents = Get-Content -Raw -LiteralPath $release
        if ($contents -match 'JetBrains' -and $contents -match 'JAVA_VERSION="(?<major>\d+)' -and [int]$Matches.major -eq $requiredVersion) { return $candidateHome }
    }
    return $null
}

$javaHome = Find-JetBrainsRuntime $javaVersion
if (-not $javaHome) {
    Write-Host "JetBrains Runtime $javaVersion is not installed yet. Asking Gradle to provision it..." -ForegroundColor Cyan
    & .\gradlew.bat compileJava "-Pminecraft_version=$MinecraftVersion" -Pmapkluss_dev_jbr=true
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $javaHome = Find-JetBrainsRuntime $javaVersion
}
if (-not $javaHome) { throw "JetBrains Runtime $javaVersion was not found. Set MAPKLUSS_CONTROL_DESK_JAVA_HOME to its home directory." }

$java = Join-Path $javaHome 'bin\java.exe'
$common = @(
    'runClient',
    "-Pminecraft_version=$MinecraftVersion",
    '-Pmapkluss_control_desk=true',
    "-Pmapkluss_library_fixture=$Fixture",
    "-Pmapkluss_library_language=$Language",
    "-Pmapkluss_control_desk_java_home=$javaHome",
    "-Pmapkluss_control_desk_java_executable=$java"
)

Write-Host "MapKluss Control Desk: $MinecraftVersion / $Fixture / $Language" -ForegroundColor Green
Write-Host 'The local browser tab controls only anonymous Library fixtures and real dev-window captures.' -ForegroundColor DarkGray

if (-not $Debug) {
    & .\gradlew.bat @common
    exit $LASTEXITCODE
}

$hotSwapSource = Join-Path $repo 'scripts\dev\MapKlussHotSwap.java'
$hotSwapClasses = Join-Path $repo 'build\control-desk\hotswap'
$clientClasses = Join-Path $repo 'build\classes\java\main'
New-Item -ItemType Directory -Force -Path $hotSwapClasses | Out-Null
& (Join-Path $javaHome 'bin\javac.exe') --add-modules jdk.jdi -d $hotSwapClasses $hotSwapSource
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jobs = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
try {
    $clientArgs = @('runClient') + $common[1..($common.Count - 1)] + @('--debug-jvm')
    $client = Start-Process -FilePath '.\gradlew.bat' -ArgumentList $clientArgs -PassThru -NoNewWindow
    $jobs.Add($client)
    $hotSwap = Start-Process -FilePath $java -ArgumentList @('--add-modules', 'jdk.jdi', '-cp', $hotSwapClasses, 'MapKlussHotSwap', '5005', $clientClasses) -PassThru -NoNewWindow
    $jobs.Add($hotSwap)
    $compiler = Start-Process -FilePath '.\gradlew.bat' -ArgumentList @('compileJava', '--continuous', "-Pminecraft_version=$MinecraftVersion", '-Pmapkluss_control_desk=true', "-Pmapkluss_library_fixture=$Fixture", "-Pmapkluss_library_language=$Language") -PassThru -NoNewWindow
    $jobs.Add($compiler)
    $client.WaitForExit()
    exit $client.ExitCode
}
finally {
    foreach ($process in $jobs) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
}

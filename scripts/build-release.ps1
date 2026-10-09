param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome
)
$ErrorActionPreference = 'Stop'
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Pass -JavaHome pointing to a JDK 25 installation.'
}
$repoRoot = Split-Path -Parent $PSScriptRoot
if ($repoRoot -match '[^\x20-\x7E]') {
    throw 'protoc requires an ASCII physical project path on Windows. Clone this repository into a path such as C:\src\YSM-NeoForge-26.3.'
}
$oldEnvironment = @{}
foreach ($key in @('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'PATH', 'GRADLE_USER_HOME', 'TEMP', 'TMP')) {
    $oldEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$buildResult = 1
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    $env:GRADLE_USER_HOME = if ($GradleUserHome) { $GradleUserHome } else { Join-Path $repoRoot '.gradle-user-home' }
    $env:TEMP = Join-Path $repoRoot '.build-downloads/tmp'
    $env:TMP = $env:TEMP
    New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
    Push-Location $repoRoot
    try {
        & .\gradlew.bat shadowJar --no-daemon --console=plain "-Porg.gradle.java.installations.paths=$env:JAVA_HOME"
        $buildResult = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    foreach ($key in $oldEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $oldEnvironment[$key], 'Process')
    }
}
exit $buildResult

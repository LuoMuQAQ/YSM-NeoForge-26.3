param([string]$NativeDll, [string]$ReleaseJar, [switch]$SkipNative)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$downloadRoot = Join-Path $repoRoot '.build-downloads'
New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null

function Get-VerifiedDownload([string]$Url, [string]$Target, [string]$Sha256) {
    if ((Test-Path -LiteralPath $Target) -and
        ((Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Sha256)) { return }
    $temporary = "$Target.download"
    Invoke-WebRequest -Uri $Url -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Sha256) {
        throw "Downloaded file hash mismatch: $Url"
    }
    Move-Item -LiteralPath $temporary -Destination $Target -Force
}

$apiRoot = Join-Path $repoRoot 'libs/compile-only'
New-Item -ItemType Directory -Force -Path $apiRoot | Out-Null
$dependencies = Get-Content -LiteralPath (Join-Path $repoRoot 'release/compile-dependencies.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($dependency in $dependencies) {
    Write-Host "Preparing compile-only API: $($dependency.filename)"
    Get-VerifiedDownload $dependency.url (Join-Path $apiRoot $dependency.filename) $dependency.sha256
}
if ($SkipNative) { return }
$inputManifest = Get-Content -LiteralPath (Join-Path $repoRoot 'release/native-input.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$nativeRoot = Join-Path $repoRoot 'src/main/resources/META-INF/native'
New-Item -ItemType Directory -Force -Path $nativeRoot | Out-Null
$targetDll = Join-Path $nativeRoot 'ysm.dll'
if (Test-Path -LiteralPath $targetDll) {
    if ((Get-FileHash -LiteralPath $targetDll -Algorithm SHA256).Hash.ToLowerInvariant() -eq $inputManifest.nativeDllSha256) {
        Write-Host 'Pinned Windows native library is already present.'
        return
    }
}
if ($NativeDll) {
    $sourceDll = (Resolve-Path -LiteralPath $NativeDll).Path
    if ((Get-FileHash -LiteralPath $sourceDll -Algorithm SHA256).Hash.ToLowerInvariant() -ne $inputManifest.nativeDllSha256) {
        throw 'Native DLL does not match the release input. To use your own native build, copy it manually and use -SkipNative.'
    }
    Copy-Item -LiteralPath $sourceDll -Destination $targetDll -Force
    return
}
if (!$ReleaseJar) {
    if (!$inputManifest.jarSha256) { throw 'Release JAR hash has not been recorded yet; supply -NativeDll.' }
    $ReleaseJar = Join-Path $downloadRoot $inputManifest.jarFilename
    $url = "https://github.com/$($inputManifest.repository)/releases/download/$($inputManifest.tag)/$($inputManifest.jarFilename)"
    Get-VerifiedDownload $url $ReleaseJar $inputManifest.jarSha256
} else {
    $ReleaseJar = (Resolve-Path -LiteralPath $ReleaseJar).Path
    if (!$inputManifest.jarSha256 -or
        ((Get-FileHash -LiteralPath $ReleaseJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $inputManifest.jarSha256)) {
        throw 'Release JAR hash mismatch.'
    }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($ReleaseJar)
try {
    $entry = $archive.GetEntry('META-INF/native/ysm.dll')
    if (!$entry) { throw 'Release JAR has no Windows native library.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetDll, $true)
} finally { $archive.Dispose() }
if ((Get-FileHash -LiteralPath $targetDll -Algorithm SHA256).Hash.ToLowerInvariant() -ne $inputManifest.nativeDllSha256) {
    throw 'Extracted native DLL hash mismatch.'
}
Write-Host 'Build inputs ready. See docs/build.md for Java and native build commands.'

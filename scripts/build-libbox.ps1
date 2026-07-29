param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$WorkDirectory = (Join-Path $env:TEMP "shield-sing-box")
)

$ErrorActionPreference = "Stop"
$version = "v1.13.12"
$expectedCommit = "1086ab2563320e0da0c23b3a491d8dfa0939dff4"
$ndkVersion = "28.0.13004108"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repositoryRoot "app\libs\libbox.aar"

if ([string]::IsNullOrWhiteSpace($AndroidSdk)) {
    throw "ANDROID_HOME is not set. Pass -AndroidSdk explicitly."
}
if (-not (Test-Path (Join-Path $AndroidSdk "ndk\$ndkVersion"))) {
    throw "Android NDK $ndkVersion is required."
}

if (Test-Path $WorkDirectory) {
    $resolvedWork = (Resolve-Path -LiteralPath $WorkDirectory).Path
    $resolvedTemp = (Resolve-Path -LiteralPath $env:TEMP).Path
    if (-not $resolvedWork.StartsWith($resolvedTemp)) {
        throw "Refusing to remove a work directory outside TEMP: $resolvedWork"
    }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}

git clone --depth 1 --branch $version https://github.com/SagerNet/sing-box.git $WorkDirectory
Push-Location $WorkDirectory
try {
    $commit = git rev-parse HEAD
    if ($commit -ne $expectedCommit) {
        throw "Unexpected sing-box commit: $commit"
    }
    $env:ANDROID_HOME = $AndroidSdk
    $env:GOTELEMETRY = "off"
    go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
    go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
    $env:Path = "$(go env GOPATH)\bin;$env:Path"
    go run ./cmd/internal/build_libbox -target android -platform "android/arm64,android/amd64"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $output) | Out-Null
    Copy-Item -LiteralPath "libbox.aar" -Destination $output -Force
} finally {
    Pop-Location
}

$hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Built $output"
Write-Host "SHA-256: $hash"

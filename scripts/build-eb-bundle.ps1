param(
    [switch]$SkipTests,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$')]
    [string]$VersionLabel
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$targetDir = Join-Path $backendDir "target"
$stagingDir = Join-Path $targetDir "elastic-beanstalk"
$bundlePath = Join-Path $targetDir "manabihub-elastic-beanstalk.zip"
$jarName = "manabihub-0.0.1-SNAPSHOT.jar"
$jarPath = Join-Path $targetDir $jarName
$linuxDecoderRoot = Join-Path $backendDir 'tools\ffmpeg-linux\ffmpeg-n9.0-latest-linux64-lgpl-9.0'
if (-not (Test-Path -LiteralPath (Join-Path $linuxDecoderRoot 'bin\ffmpeg'))) {
    throw 'Linux FFmpeg is missing. Run scripts/setup-cohort-audio-linux.ps1 before building the AWS bundle.'
}
$decoderHash = (Get-FileHash -LiteralPath (Join-Path $linuxDecoderRoot 'bin\ffmpeg') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($decoderHash -ne '953632dece404bb1461a4a2285f0191a6105d72c81f9da577eb54326a7fad49a') {
    throw 'The Linux decoder checksum does not match the verified deployment version.'
}

$safeDirectory = $repoRoot.Replace('\', '/')
$gitCommitOutput = & git -c "safe.directory=$safeDirectory" -C $repoRoot rev-parse --short=12 HEAD
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitCommitOutput)) {
    throw "Could not determine the Git commit used for this backend build."
}
$gitCommit = $gitCommitOutput.Trim()

$trackedChanges = & git -c "safe.directory=$safeDirectory" -C $repoRoot status --porcelain --untracked-files=no
if ($LASTEXITCODE -ne 0) {
    throw "Could not determine whether the Git worktree is clean."
}
if ($trackedChanges) {
    $gitCommit = "$gitCommit-dirty"
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $backendDir "pom.xml")
$projectVersion = $pom.project.version
$deploymentVersion = if ([string]::IsNullOrWhiteSpace($VersionLabel)) {
    "$projectVersion+$gitCommit"
} else {
    $VersionLabel
}

Push-Location $backendDir
try {
    $maven = if (Test-Path ".\mvnw.cmd") { ".\mvnw.cmd" } else { "mvn" }
    $arguments = @(
        "clean",
        "package",
        "-Dbuild.deployment.version=$deploymentVersion",
        "-Dbuild.git.commit=$gitCommit"
    )
    if ($SkipTests) {
        $arguments += "-DskipTests"
    }

    & $maven @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Expected JAR was not created: $jarPath"
}

if (Test-Path -LiteralPath $stagingDir) {
    $resolvedStagingDir = [IO.Path]::GetFullPath($stagingDir)
    $resolvedTargetDir = [IO.Path]::GetFullPath($targetDir)
    if (-not $resolvedStagingDir.StartsWith($resolvedTargetDir + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The staging cleanup target is outside backend/target.'
    }
    Remove-Item -LiteralPath $stagingDir -Recurse -Force
}
New-Item -ItemType Directory -Path $stagingDir | Out-Null

Copy-Item -LiteralPath $jarPath -Destination (Join-Path $stagingDir $jarName)
Copy-Item -LiteralPath (Join-Path $backendDir "Procfile") -Destination (Join-Path $stagingDir "Procfile")
$decoderDestination = Join-Path $stagingDir 'tools\ffmpeg'
New-Item -ItemType Directory -Path $decoderDestination -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $linuxDecoderRoot 'bin\ffmpeg') -Destination (Join-Path $decoderDestination 'ffmpeg')
Copy-Item -LiteralPath (Join-Path $linuxDecoderRoot 'LICENSE.txt') -Destination (Join-Path $decoderDestination 'LICENSE.txt')

if (Test-Path -LiteralPath (Join-Path $backendDir ".platform")) {
    Copy-Item -LiteralPath (Join-Path $backendDir ".platform") -Destination (Join-Path $stagingDir ".platform") -Recurse
}

if (Test-Path -LiteralPath $bundlePath) {
    Remove-Item -LiteralPath $bundlePath -Force
}

Push-Location $stagingDir
try {
    tar -a -c -f $bundlePath $jarName Procfile .platform tools
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create zip bundle using tar command. Exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host "Elastic Beanstalk bundle created:" -ForegroundColor Green
Write-Host "  $bundlePath"
Write-Host "  Version: $deploymentVersion"
Write-Host "  Git commit: $gitCommit"
Write-Host "The ZIP root contains Procfile, .platform config, and $jarName."

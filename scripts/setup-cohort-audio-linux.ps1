$ErrorActionPreference = 'Stop'
$taskRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskTools = Join-Path $taskRepo 'backend\tools'
$taskArchive = Join-Path $taskTools 'ffmpeg-linux-lgpl.tar.xz'
$taskExtract = Join-Path $taskTools 'ffmpeg-linux'
$taskPrefix = 'ffmpeg-n9.0-latest-linux64-lgpl-9.0'
$taskDecoder = Join-Path $taskExtract ($taskPrefix + '\bin\ffmpeg')
$taskArchiveHash = 'e0fcb7fd5cddba97804258a89d1c44b6ec338a0874e4e9ea93827db05a679241'
$taskDecoderHash = '953632dece404bb1461a4a2285f0191a6105d72c81f9da577eb54326a7fad49a'
if ((Test-Path -LiteralPath $taskDecoder) -and
    (Get-FileHash -LiteralPath $taskDecoder -Algorithm SHA256).Hash.ToLowerInvariant() -eq $taskDecoderHash) {
    Write-Output "Verified Linux decoder available: $taskDecoder"
    return
}
New-Item -ItemType Directory -Path $taskTools -Force | Out-Null
if (-not (Test-Path -LiteralPath $taskArchive)) {
    Invoke-WebRequest -Uri 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n9.0-latest-linux64-lgpl-9.0.tar.xz' -OutFile $taskArchive -TimeoutSec 600
}
if ((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskArchiveHash) {
    throw 'The vendor archive changed. Verify the new publisher digest before updating the pinned hashes; no unverified executable will be used.'
}
New-Item -ItemType Directory -Path $taskExtract -Force | Out-Null
& tar -xJf $taskArchive -C $taskExtract "$taskPrefix/bin/ffmpeg" "$taskPrefix/LICENSE.txt"
if ($LASTEXITCODE -ne 0) { throw 'Linux decoder extraction failed.' }
if ((Get-FileHash -LiteralPath $taskDecoder -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskDecoderHash) {
    throw 'Extracted Linux decoder checksum does not match.'
}
Write-Output "Verified Linux decoder prepared: $taskDecoder"

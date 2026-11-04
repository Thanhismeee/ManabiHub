$ErrorActionPreference = 'Stop'
$taskRepo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskMediaDir = Join-Path $taskRepo 'backend\tools\ffmpeg'
$taskExecutable = Join-Path $taskMediaDir 'ffmpeg.exe'
if (Test-Path -LiteralPath $taskExecutable) {
    Write-Output "Audio decoder available: $taskExecutable"
    return
}
New-Item -ItemType Directory -Path $taskMediaDir -Force | Out-Null
$taskArchive = Join-Path $taskMediaDir ('download-' + [guid]::NewGuid().ToString('N') + '.zip')
$taskUrl = 'https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip'
$taskExpected = '60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba'
try {
    Invoke-WebRequest -Uri $taskUrl -OutFile $taskArchive -TimeoutSec 300
    if ((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskExpected) {
        throw 'FFmpeg archive checksum does not match the pinned publisher checksum.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskZip = [System.IO.Compression.ZipFile]::OpenRead($taskArchive)
    try {
        foreach ($taskName in @('ffmpeg.exe', 'ffprobe.exe', 'LICENSE', 'README.txt')) {
            $taskEntry = $taskZip.Entries | Where-Object { $_.Name -ceq $taskName } | Select-Object -First 1
            if ($taskEntry) {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, (Join-Path $taskMediaDir $taskName), $true)
            }
        }
    } finally { $taskZip.Dispose() }
    if (-not (Test-Path -LiteralPath $taskExecutable)) { throw 'FFmpeg executable missing from archive.' }
    Write-Output "Installed audio decoder: $taskExecutable"
} finally {
    # Only remove this script's uniquely named download, with the full target verified.
    $taskResolvedArchive = [System.IO.Path]::GetFullPath($taskArchive)
    if (-not $taskResolvedArchive.StartsWith($taskMediaDir + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Download cleanup target is outside the media directory.'
    }
    if (Test-Path -LiteralPath $taskResolvedArchive) { Remove-Item -LiteralPath $taskResolvedArchive -Force }
}

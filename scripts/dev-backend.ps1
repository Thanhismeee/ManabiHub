$ErrorActionPreference = "Stop"

# Auto-detect and configure JDK 21 if installed but not set as default
$jdkPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files (x86)\Java",
    "$env:USERPROFILE\.jdks"
)
$jdk21Folder = $null
foreach ($path in $jdkPaths) {
    if (Test-Path $path) {
        $subfolders = Get-ChildItem -Path $path -Directory -Filter "*21*" -ErrorAction SilentlyContinue
        if ($subfolders) {
            $jdk21Folder = $subfolders[0].FullName
            break
        }
    }
}

if ($jdk21Folder) {
    Write-Host "Auto-detected JDK 21 at: $jdk21Folder" -ForegroundColor Green
    Write-Host "Configuring JAVA_HOME and PATH for this session..." -ForegroundColor Green
    $env:JAVA_HOME = $jdk21Folder
    $env:PATH = "$jdk21Folder\bin;$env:PATH"
}

Write-Host "Checking Java version..." -ForegroundColor Cyan

$javaCheck = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCheck) {
    Write-Host "ERROR: Java not found. Please install JDK 21." -ForegroundColor Red
    exit 1
}

# Run java -version and capture stderr (where Java prints version info) using a temp file
$tempFile = [System.IO.Path]::GetTempFileName()
try {
    cmd /c "java -version 2> `"$tempFile`""
    $javaVersionOutput = Get-Content $tempFile
} finally {
    if (Test-Path $tempFile) {
        Remove-Item $tempFile
    }
}

$isJava21 = $false
foreach ($line in $javaVersionOutput) {
    if ($line -match 'version "21\.') {
        $isJava21 = $true
        break
    }
}

if (-not $isJava21) {
    Write-Host "WARNING: ManabiHub requires JDK 21 for local development. Current Java appears to be different." -ForegroundColor Yellow
    foreach ($line in $javaVersionOutput) {
        Write-Host $line -ForegroundColor Yellow
    }
    Write-Host ""
}


$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }
$dbPort = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5433 }
Write-Host "Checking PostgreSQL at ${dbHost}:${dbPort}..." -ForegroundColor Cyan

$tcpClient = New-Object System.Net.Sockets.TcpClient
$dbReachable = $false
try {
    $connect = $tcpClient.BeginConnect($dbHost, $dbPort, $null, $null)
    if ($connect.AsyncWaitHandle.WaitOne(1500, $false)) {
        $tcpClient.EndConnect($connect)
        $dbReachable = $true
    }
} catch {
    $dbReachable = $false
} finally {
    $tcpClient.Close()
}

if (-not $dbReachable) {
    Write-Host "ERROR: PostgreSQL is not reachable at ${dbHost}:${dbPort}." -ForegroundColor Red
    Write-Host "Start Docker Desktop, then run this from the repository root:" -ForegroundColor Yellow
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\dev-db-up.ps1" -ForegroundColor Yellow
    Write-Host "After the database is healthy, run the backend task again." -ForegroundColor Yellow
    exit 1
}

Write-Host "PostgreSQL is reachable." -ForegroundColor Green
Write-Host "Starting Spring Boot backend..." -ForegroundColor Cyan
Set-Location -Path "backend"
$mvnCommand = if (Test-Path ".\mvnw.cmd") { ".\mvnw.cmd" } else { "mvn" }
& $mvnCommand spring-boot:run "-Dspring-boot.run.profiles=local"

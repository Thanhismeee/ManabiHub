$ErrorActionPreference = "Stop"

Write-Host "Checking Docker Desktop..." -ForegroundColor Cyan
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running."
    }
} catch {
    Write-Host "ERROR: Docker Desktop is not running. Please start Docker Desktop and try again." -ForegroundColor Red
    exit 1
}

$composeFile = "deploy/docker-compose.local.yml"
if (-not (Test-Path $composeFile)) {
    Write-Host "ERROR: Could not find $composeFile" -ForegroundColor Red
    exit 1
}

Write-Host "Starting PostgreSQL database container..." -ForegroundColor Cyan
docker compose -f $composeFile up -d

Write-Host "Waiting for database to be healthy..." -ForegroundColor Cyan
$maxAttempts = 30
$attempt = 0
$isHealthy = $false

while ($attempt -lt $maxAttempts -and -not $isHealthy) {
    Start-Sleep -Seconds 2
    $status = docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{end}}" manabihub-postgres 2>$null
    if ($status -eq "healthy") {
        $isHealthy = $true
    }
    $attempt++
}

if (-not $isHealthy) {
    Write-Host "WARNING: Database did not become healthy within the expected time." -ForegroundColor Yellow
} else {
    Write-Host "Database is up and healthy!" -ForegroundColor Green
}

Write-Host "`n=== Connection Info ===" -ForegroundColor Green
Write-Host "Host:     localhost"
Write-Host "Port:     5433"
Write-Host "Database: manabihub"
Write-Host "Username: manabihub"
Write-Host "Password: manabihub_dev_password"
Write-Host "======================="

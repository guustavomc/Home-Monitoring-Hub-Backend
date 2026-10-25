<#
.SYNOPSIS
    Starts Mosquitto and PostgreSQL with Docker Compose, then runs the backend.

.DESCRIPTION
    Reads DB_USERNAME and DB_PASSWORD from .env, waits until PostgreSQL is
    healthy, sets the environment variables the app expects and runs
    "mvn spring-boot:run" from the home-monitoring-hub folder.

.PARAMETER ServiceHost
    Host running Mosquitto and PostgreSQL. Defaults to localhost (this PC).
    Use the Raspberry Pi's IP once the services run there.

.PARAMETER SkipDocker
    Don't start the Docker containers (e.g. when they run on the Pi).

.EXAMPLE
    .\run-local.ps1

.EXAMPLE
    .\run-local.ps1 -ServiceHost 192.168.1.50 -SkipDocker
#>
param(
    [string]$ServiceHost = "localhost",
    [switch]$SkipDocker
)

$repoRoot = $PSScriptRoot

# ---- Load DB_USERNAME / DB_PASSWORD from .env ----
$envFile = Join-Path $repoRoot ".env"
if (-not (Test-Path $envFile)) {
    throw ".env not found. Copy .env.example to .env and set DB_PASSWORD."
}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
        $value = $Matches[2].Trim('"').Trim("'")
        Set-Item -Path "Env:$($Matches[1])" -Value $value
    }
}

# ---- Start Mosquitto and PostgreSQL ----
if (-not $SkipDocker) {
    Write-Host "Starting Mosquitto and PostgreSQL..." -ForegroundColor Cyan
    docker compose -f (Join-Path $repoRoot "docker-compose.yml") up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed. Is Docker Desktop running?"
    }

    Write-Host "Waiting for PostgreSQL to be healthy..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $status = docker inspect --format "{{.State.Health.Status}}" postgres
        if ($status -eq "healthy") { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ($status -ne "healthy") {
        throw "PostgreSQL did not become healthy within 60 s. Check 'docker compose logs postgres'."
    }
}

# ---- Point the app at the services ----
$env:DB_URL = "jdbc:postgresql://${ServiceHost}:5432/home_monitoring"
$env:MQTT_BROKER_URL = "tcp://${ServiceHost}:1883"

Write-Host "Database: $env:DB_URL (user $env:DB_USERNAME)" -ForegroundColor Cyan
Write-Host "Broker:   $env:MQTT_BROKER_URL" -ForegroundColor Cyan

# ---- Run the backend ----
Push-Location (Join-Path $repoRoot "home-monitoring-hub")
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}

# Load environment variables from .env.local and run auth-service
$envFile = Join-Path $PSScriptRoot ".env.local"
if (Test-Path $envFile) {
    Write-Host "Loading environment from $envFile..." -ForegroundColor Cyan
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s]+=' } | ForEach-Object {
        $key, $val = $_ -split '=', 2
        [System.Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim(), "Process")
    }
} else {
    Write-Warning ".env.local not found. Using default environment variables."
}

Write-Host "Starting auth-service on port $env:SERVER_PORT..." -ForegroundColor Green
mvn spring-boot:run

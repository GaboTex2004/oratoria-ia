[CmdletBinding()]
param(
    [switch]$Models
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'docker compose config fallo.' }

    & docker compose --profile test run --rm backend-tests
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la suite Java en el PostgreSQL efimero.' }
    & docker compose --profile test run --rm ai-tests
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Ruff o la suite Python.' }
    & docker compose --profile test run --rm frontend-tests
    if ($LASTEXITCODE -ne 0) { throw 'Fallo frontend test, lint o build.' }

    if ($Models) {
        $audioPath = Join-Path $projectRoot '.tools/synthetic-spanish.wav'
        if (!(Test-Path -LiteralPath $audioPath)) {
            throw 'Falta .tools/synthetic-spanish.wav. Ejecuta primero .\scripts\New-SyntheticAudio.ps1.'
        }
        $env:E2E_MODELS = '1'
        $env:E2E_SYNTHETIC_AUDIO = '/.tools/synthetic-spanish.wav'
    } else {
        $env:E2E_MODELS = '0'
        $env:E2E_SYNTHETIC_AUDIO = ''
    }
    & docker compose --profile test run --rm playwright-tests
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Playwright.' }
} finally {
    Remove-Item Env:E2E_MODELS -ErrorAction SilentlyContinue
    Remove-Item Env:E2E_SYNTHETIC_AUDIO -ErrorAction SilentlyContinue
    Pop-Location
}

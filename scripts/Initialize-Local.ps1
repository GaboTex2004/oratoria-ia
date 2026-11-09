param([int]$DatabasePort = 5435, [switch]$UseGpu)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function New-LocalSecret {
    $secretBytes = New-Object byte[] 36
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($secretBytes)
    $generator.Dispose()
    return [Convert]::ToBase64String($secretBytes)
}
function Read-EnvValue([string]$Path, [string]$Key) {
    if (Test-Path -LiteralPath $Path) {
        $line = Get-Content -LiteralPath $Path | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
        if ($line) { return $line.Substring($Key.Length + 1) }
    }
    return ''
}
function Ensure-EnvValue([string]$Path, [string]$Key, [string]$Value) {
    $existingValue = Read-EnvValue $Path $Key
    if ([string]::IsNullOrWhiteSpace($existingValue) -or $existingValue.StartsWith('replace-with')) {
        $preservedLines = @()
        if (Test-Path -LiteralPath $Path) { $preservedLines = @(Get-Content -LiteralPath $Path | Where-Object { $_ -notmatch "^$Key=" }) }
        [IO.File]::WriteAllLines($Path, @($preservedLines) + "$Key=$Value", [Text.UTF8Encoding]::new($false))
    }
}

$backendEnv = Join-Path $projectRoot 'backend/.env'
$aiEnv = Join-Path $projectRoot 'ai-service/.env'
$rootEnv = Join-Path $projectRoot '.env'
Ensure-EnvValue $rootEnv 'POSTGRES_PORT' "$DatabasePort"
Ensure-EnvValue $backendEnv 'DB_URL' "jdbc:postgresql://127.0.0.1:$DatabasePort/oratoria_db"
Ensure-EnvValue $backendEnv 'JWT_SECRET' (New-LocalSecret)
$sharedKey = Read-EnvValue $backendEnv 'INTERNAL_API_KEY'
if (!$sharedKey -or $sharedKey.StartsWith('replace-with')) { $sharedKey = Read-EnvValue $aiEnv 'INTERNAL_API_KEY' }
if (!$sharedKey -or $sharedKey.StartsWith('replace-with')) { $sharedKey = New-LocalSecret }
Ensure-EnvValue $backendEnv 'INTERNAL_API_KEY' $sharedKey
Ensure-EnvValue $aiEnv 'INTERNAL_API_KEY' $sharedKey
if ((Read-EnvValue $backendEnv 'INTERNAL_API_KEY') -ne (Read-EnvValue $aiEnv 'INTERNAL_API_KEY')) {
    throw 'Las claves internas existentes no coinciden. Corrigelas antes de iniciar.'
}
Ensure-EnvValue $backendEnv 'APP_PROFILE' 'dev'
Ensure-EnvValue $backendEnv 'BILLING_DEMO_ENABLED' 'true'
Ensure-EnvValue $backendEnv 'BILLING_PREMIUM_PRICE' '49.00'
Ensure-EnvValue $backendEnv 'BILLING_GRACE_DAYS' '3'
# Explicit adoption of the existing Hibernate schema, backed up before first use.
Ensure-EnvValue $backendEnv 'FLYWAY_BASELINE_ON_MIGRATE' 'true'
Ensure-EnvValue $backendEnv 'ADMIN_EMAIL' 'admin@oratoria.test'
Ensure-EnvValue $backendEnv 'ADMIN_PASSWORD' (New-LocalSecret)
Ensure-EnvValue $backendEnv 'DEMO_FREE_EMAIL' 'free@oratoria.test'
Ensure-EnvValue $backendEnv 'DEMO_FREE_PASSWORD' (New-LocalSecret)
Ensure-EnvValue $backendEnv 'DEMO_PREMIUM_EMAIL' 'premium@oratoria.test'
Ensure-EnvValue $backendEnv 'DEMO_PREMIUM_PASSWORD' (New-LocalSecret)
Ensure-EnvValue $backendEnv 'AI_SERVICE_TIMEOUT_SECONDS' '300'
Ensure-EnvValue $aiEnv 'OLLAMA_URL' 'http://127.0.0.1:11434'
Ensure-EnvValue $aiEnv 'OLLAMA_MODEL' $(if ($UseGpu) { 'qwen3:4b-instruct-2507-q4_K_M' } else { 'qwen2.5:1.5b' })
Ensure-EnvValue $aiEnv 'LLM_TIMEOUT_SECONDS' '240'
Ensure-EnvValue $aiEnv 'LLM_NUM_GPU' $(if ($UseGpu) { '99' } else { '0' })
$frontendEnv = Join-Path $projectRoot 'frontend/.env'
Ensure-EnvValue $frontendEnv 'VITE_API_URL' 'http://localhost:8081/api'
Write-Output 'Configuracion local preparada. Las credenciales aleatorias estan en backend/.env; no las subas a Git.'

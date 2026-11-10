[CmdletBinding()]
param(
    [ValidateRange(300, 7200)]
    [int]$WaitTimeoutSeconds = 3600
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env'

function New-RandomSecret([int]$ByteCount = 48) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Read-EnvironmentFile {
    $values = @{}
    if (Test-Path -LiteralPath $environmentFile) {
        foreach ($line in [IO.File]::ReadAllLines($environmentFile)) {
            if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
        }
    }
    return $values
}

function Add-MissingEnvironmentValue([hashtable]$Values, [string]$Name, [string]$Value) {
    if ($Values.ContainsKey($Name) -and $Values[$Name] -ne '' -and !$Values[$Name].StartsWith('replace-with-')) {
        return
    }
    if ($Values.ContainsKey($Name) -and $Values[$Name].StartsWith('replace-with-')) {
        $lines = [IO.File]::ReadAllLines($environmentFile)
        for ($index = 0; $index -lt $lines.Length; $index++) {
            if ($lines[$index].StartsWith($Name + '=')) {
                $lines[$index] = $Name + '=' + $Value
                break
            }
        }
        [IO.File]::WriteAllLines($environmentFile, $lines, [Text.UTF8Encoding]::new($false))
        $Values[$Name] = $Value
        return
    }
    $prefix = ''
    if ((Test-Path -LiteralPath $environmentFile) -and (Get-Item -LiteralPath $environmentFile).Length -gt 0) {
        $content = [IO.File]::ReadAllText($environmentFile)
        if (!$content.EndsWith("`n")) { $prefix = [Environment]::NewLine }
    }
    [IO.File]::AppendAllText(
        $environmentFile,
        $prefix + $Name + '=' + $Value + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )
    $Values[$Name] = $Value
}

if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI no esta instalado o no esta en PATH.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop no esta disponible. Inicialo y vuelve a ejecutar el script.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 no esta disponible.' }

$values = Read-EnvironmentFile
Add-MissingEnvironmentValue $values 'FRONTEND_PORT' '15173'
Add-MissingEnvironmentValue $values 'ORATORIA_DB_NAME' 'oratoriaai_local'
Add-MissingEnvironmentValue $values 'ORATORIA_DB_USER' 'oratoria'
Add-MissingEnvironmentValue $values 'ORATORIA_DB_PASSWORD' (New-RandomSecret 36)
Add-MissingEnvironmentValue $values 'JWT_SECRET' (New-RandomSecret 64)
Add-MissingEnvironmentValue $values 'INTERNAL_API_KEY' (New-RandomSecret 48)
Add-MissingEnvironmentValue $values 'OLLAMA_MODEL' 'qwen2.5:1.5b'
Add-MissingEnvironmentValue $values 'APP_PROFILE' 'dev'
Add-MissingEnvironmentValue $values 'BILLING_DEMO_ENABLED' 'true'
Add-MissingEnvironmentValue $values 'ADMIN_EMAIL' 'admin@oratoria.local'
Add-MissingEnvironmentValue $values 'ADMIN_PASSWORD' (New-RandomSecret 24)

Push-Location $projectRoot
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'La configuracion de Docker Compose no es valida.' }

    Write-Host 'Construyendo e iniciando OratoriaAI. La primera descarga de modelos puede tardar varios minutos...'
    & docker compose up -d --build --wait --wait-timeout $WaitTimeoutSeconds
    if ($LASTEXITCODE -ne 0) {
        & docker compose ps
        throw 'El stack no alcanzo un estado saludable. Revisa: docker compose logs --tail 200'
    }

    & docker compose exec -T ai-service python scripts/download_models.py --whisper base --pose --verify-only
    if ($LASTEXITCODE -ne 0) { throw 'Los modelos Whisper/MediaPipe no superaron la verificacion.' }
    & docker compose exec -T ollama ollama show $values['OLLAMA_MODEL'] *> $null
    if ($LASTEXITCODE -ne 0) { throw 'El modelo configurado de Ollama no esta disponible.' }

    $baseUrl = 'http://127.0.0.1:' + $values['FRONTEND_PORT']
    Invoke-WebRequest -UseBasicParsing -Uri ($baseUrl + '/healthz') -TimeoutSec 15 | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri ($baseUrl + '/api/test') -TimeoutSec 30 | Out-Null
    & docker compose ps
    Write-Host ('OratoriaAI esta disponible en ' + $baseUrl)
    Write-Host 'Las credenciales y secretos locales permanecen en .env y no se imprimen.'
} finally {
    Pop-Location
}

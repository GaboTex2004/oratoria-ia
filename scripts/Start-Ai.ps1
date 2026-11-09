$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExecutable = Join-Path $projectRoot 'ai-service/.venv/Scripts/python.exe'
if (!(Test-Path -LiteralPath $pythonExecutable)) { throw 'Prepara primero el entorno virtual de ai-service.' }
$env:HF_HUB_OFFLINE = '1'
Push-Location (Join-Path $projectRoot 'ai-service') -ErrorAction Stop
try {
    & $pythonExecutable -m uvicorn app.main:app --host 127.0.0.1 --port 8000
    if ($LASTEXITCODE -ne 0) { throw 'FastAPI termino con un error. Revisa el diagnostico anterior.' }
}
finally { Pop-Location }

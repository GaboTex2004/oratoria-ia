$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $projectRoot '.tools/ollama/ollama.exe'
if (!(Test-Path -LiteralPath $runtime)) { throw 'Ejecuta primero scripts/Install-Ollama.ps1.' }
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_MODELS = Join-Path $projectRoot 'ai-service/models/ollama'
$env:OLLAMA_NO_CLOUD = '1'
& $runtime serve
if ($LASTEXITCODE -ne 0) { throw 'Ollama termino con un error. Revisa su diagnostico.' }

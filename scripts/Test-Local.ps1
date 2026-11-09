$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$env:JAVA_HOME -and (Test-Path 'C:/Program Files/Java/jdk-21')) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-21' }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -B test
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la suite Java. Comprueba oratoria_test y TEST_DB_*.' }
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'ai-service')
try {
    & .\.venv\Scripts\python.exe -m ruff check app tests scripts
    if ($LASTEXITCODE -ne 0) { throw 'Ruff detecto errores.' }
    & .\.venv\Scripts\python.exe -m pytest -m 'not models' -q
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la suite Python.' }
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la suite frontend.' }
    & npm.cmd run lint
    if ($LASTEXITCODE -ne 0) { throw 'ESLint detecto errores.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion frontend.' }
} finally { Pop-Location }

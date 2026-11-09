$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDirectory = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force $toolsDirectory | Out-Null
$archivePath = Join-Path $toolsDirectory 'ollama-windows-amd64.zip'
$expectedDigest = '9ca74a7ab66ec86870b1eecac7df160df08dc4e3a291ab5d2d59bcf9310221c6'
if (!(Test-Path -LiteralPath $archivePath)) {
    Write-Output 'Descargando Ollama v0.40.15 oficial (~1.6 GB). No se activa inferencia cloud.'
    & curl.exe -L --fail --silent --show-error 'https://github.com/ollama/ollama/releases/download/v0.40.15/ollama-windows-amd64.zip' -o $archivePath
    if ($LASTEXITCODE -ne 0) { throw 'No se completo la descarga de Ollama.' }
}
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLower() -ne $expectedDigest) {
    throw 'El SHA256 no coincide con la publicacion oficial. No se extrae ni se ejecuta el archivo.'
}
$runtimeDirectory = Join-Path $toolsDirectory 'ollama'
if (!(Test-Path (Join-Path $runtimeDirectory 'ollama.exe'))) {
    Expand-Archive -LiteralPath $archivePath -DestinationPath $runtimeDirectory
}
Write-Output 'Ollama verificado. Inicia scripts/Start-Ollama.ps1 en una terminal dedicada.'

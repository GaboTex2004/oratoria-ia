$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.tools') | Out-Null
Add-Type -AssemblyName System.Speech
$synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $synthesizer.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'es-*' } | Select-Object -First 1
    if (!$voice) { throw 'Instala una voz local en espanol de Windows para esta prueba.' }
    $synthesizer.SelectVoice($voice.VoiceInfo.Name)
    $synthesizer.SetOutputToWaveFile((Join-Path $projectRoot '.tools/synthetic-spanish.wav'))
    $synthesizer.Speak('Buenos dias. Hoy presentamos una plataforma para mejorar la comunicacion oral. Primero organizamos las ideas. Despues practicamos con ejemplos. Por ejemplo, una entrevista permite explicar nuestra experiencia. Finalmente revisamos los resultados para aprender y mejorar.')
} finally { $synthesizer.Dispose() }
Write-Output 'Audio sintetico de prueba generado en .tools/synthetic-spanish.wav. No es una grabacion humana.'

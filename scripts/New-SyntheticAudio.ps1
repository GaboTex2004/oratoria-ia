$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.tools') | Out-Null
$audioPath = Join-Path $projectRoot '.tools/synthetic-spanish.wav'
$text = 'Buenos dias. Hoy presentamos una plataforma para mejorar la comunicacion oral. Primero organizamos las ideas. Despues practicamos con ejemplos. Por ejemplo, una entrevista permite explicar nuestra experiencia. Finalmente revisamos los resultados para aprender y mejorar.'

try {
    Add-Type -AssemblyName System.Speech
    $synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        $voice = $synthesizer.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'es-*' } | Select-Object -First 1
        if (!$voice) { throw 'No se encontro una voz de Windows en espanol.' }
        $synthesizer.SelectVoice($voice.VoiceInfo.Name)
        $synthesizer.SetOutputToWaveFile($audioPath)
        $synthesizer.Speak($text)
    } finally {
        if ($synthesizer) { $synthesizer.Dispose() }
    }
} catch {
    # System.Speech can fail to enumerate otherwise valid voices on some Windows builds.
    # SAPI is the native fallback and writes the same local, synthetic WAV fixture.
    if (Test-Path -LiteralPath $audioPath) { Remove-Item -LiteralPath $audioPath -Force }
    $sapi = $null
    $stream = $null
    try {
        $sapi = New-Object -ComObject SAPI.SpVoice
        $voices = $sapi.GetVoices()
        $spanishVoice = $null
        for ($index = 0; $index -lt $voices.Count; $index++) {
            $candidate = $voices.Item($index)
            if ($candidate.GetDescription() -match 'Spanish|Espa.ol') {
                $spanishVoice = $candidate
                break
            }
        }
        if (!$spanishVoice) { throw 'Instala una voz local en espanol de Windows para esta prueba.' }
        $stream = New-Object -ComObject SAPI.SpFileStream
        $stream.Format.Type = 22 # SAFT22kHz16BitMono
        $stream.Open($audioPath, 3, $false) # SSFMCreateForWrite
        $sapi.Voice = $spanishVoice
        $sapi.AudioOutputStream = $stream
        [void]$sapi.Speak($text)
        $stream.Close()
    } finally {
        if ($stream) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($stream) }
        if ($sapi) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($sapi) }
    }
}
Write-Output 'Audio sintetico de prueba generado en .tools/synthetic-spanish.wav. No es una grabacion humana.'

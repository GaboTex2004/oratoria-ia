# Evidencia de ejecución

Fecha: 2026-11-08, America/La_Paz. No incluye credenciales ni datos humanos.

- `navegador-final.txt`: resultado real de Playwright, ocho tests aprobados.
- `resultados-sinteticos.png`: captura real después de Whisper y Qwen sobre WAV
  generado con voz local de Windows. No corresponde a un estudiante.
- `camara-ficticia.png`: captura del resultado con dispositivo ficticio del navegador;
  no valida exactitud de postura de una persona real.
- `movil-375.png`: interfaz real en viewport de 375 píxeles.
- `modelos-cpu-sinteticos.json`: cinco comprobaciones con Qwen2.5 1.5B.
- `modelos-gpu-sinteticos.json`: cinco comprobaciones con Qwen3 4B/GPU. Ambos JSON
  preceden al último ajuste menor de reglas v2; el recorrido final usa v2.0-local.
- `archivos-modificados.txt`: inventario relativo a la base 2784c2e, sin pesos,
  herramientas privadas, secretos, audio ni dependencias instaladas.

Los PDF reales generados por la prueba permanecen en `.tools/browser-results`:
`synthetic-local-report.pdf` y `synthetic-progress-report.pdf`. Esa carpeta se
excluye de Git para evitar publicar accidentalmente otras capturas privadas.

# Plan de pruebas reproducibles

Fecha: 2026-11-08. Entorno y modelos: [modelos locales](../ia/MODELOS_LOCALES.md).
Resultados: [informe](INFORME_DE_PRUEBAS.md). No usar datos personales ni la base
de desarrollo como base de pruebas.

| Nivel | Comando / procedimiento | Criterio |
|---|---|---|
| Java | `scripts/Test-Local.ps1` invoca Maven test en backend | 49 pruebas; DB oratoria_test; auth, propiedad, cuotas, privacidad y migraciones |
| Python | El mismo script ejecuta Ruff y pytest sin modelos | 50 pruebas; errores locales, reglas, acústica, límites y contratos |
| Frontend | El mismo script ejecuta npm test, lint y build | 14 pruebas y compilación sin errores |
| Migración nueva | Maven BackendApplicationTests con TEST_DB_URL de oratoria_migration_test inicialmente vacía | V1, V2 y V3 aplicadas; inicio validado |
| Dependencias JS | `npm audit --audit-level=moderate` desde frontend | Cero avisos en ejecución registrada |
| Dependencias Python | `python -m pip check` en el entorno virtual | Sin conflictos; no equivale a búsqueda de CVE |
| Modelos reales | Desde ai-service: `python -m scripts.verify_models --audio ../.tools/synthetic-spanish.wav --output ../.tools/model-verification.json` | Cinco comprobaciones: Whisper, evaluación, turno, feedback y pose sin persona |
| Navegador | Desde frontend: `npm run test:e2e` con servicios disponibles | Seis anchos 320/375/580/768/980/1440; sin desborde horizontal |
| ADMIN | E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD por entorno | Login real y creación de ejercicio inactivo |
| Recorrido local | E2E_MODELS=1 y E2E_SYNTHETIC_AUDIO con ruta absoluta al WAV sintético | Registro, grabación, Whisper/LLM, simulación, PDF, cámara ficticia y borrado |

El WAV se genera con `scripts/New-SyntheticAudio.ps1`; las opciones de Playwright
usan dispositivos ficticios. No se accedió a micrófono/cámara humanos. Los tests
de navegador crean cuentas temporales y ejercicios inactivos identificados; no
ejecutarlos en producción. La simulación real requiere varios minutos por carga
de modelos; los errores no se sustituyen por respuestas prefabricadas.

Pendiente fuera de esta ejecución: precisión WER sobre corpus humano, concordancia
de rúbrica con docentes, exactitud de postura, navegadores móviles físicos,
accesibilidad con lector de pantalla, carga concurrente y cobertura de líneas.

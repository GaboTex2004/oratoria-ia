# Servicio de IA local

FastAPI conectado a Spring: Whisper español, métricas acústicas, evaluación por
reglas y Qwen/Ollama, conversación y MediaPipe Pose. No usa proveedores cloud.

Instalación e inicio probados: [README raíz](../README.md).
Ejecutar desde la raíz `scripts/Start-Ai.ps1` después de preparar entorno y modelos.
API interna protegida con INTERNAL_API_KEY; no exponerla al navegador.

- [Modelos, perfiles CPU/GPU y mediciones](../docs/ia/MODELOS_LOCALES.md)
- [Fórmulas y rúbricas](../docs/ia/METRICAS_Y_RUBRICAS.md)
- [Dataset, consentimiento y ausencia de entrenamiento propio](../docs/ia/DATASET_Y_PROCEDENCIA.md)
- [Contratos API](../docs/API.md) y [pruebas](../docs/pruebas/PLAN_DE_PRUEBAS.md)

`requirements.txt` fija dependencias directas; `requirements.lock.txt` registra el
entorno completo probado en Windows/Python 3.13. Las pruebas sin pesos se ejecutan
con `.venv/Scripts/python.exe -m pytest -m 'not models' -q` desde esta carpeta.

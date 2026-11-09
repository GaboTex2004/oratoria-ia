# Inferencia local, perfiles y procedencia

No se llama a OpenAI API, Groq, Gemini ni a servicios de visión remotos. Una descarga
inicial desde fuentes oficiales prepara los pesos; el servidor no los descarga.
Las pruebas de voz se ejecutaron con `HF_HUB_OFFLINE=1`.

## Componentes seleccionados

| Componente | Implementación y licencia / fuente | Archivo o identificador |
|---|---|---|
| Voz | [faster-whisper](https://github.com/SYSTRAN/faster-whisper), MIT; pesos Whisper preentrenados | [Systran/faster-whisper-base](https://huggingface.co/Systran/faster-whisper-base), revisión `ebe41f70d5b6dfa9166e2c581c45c9c0cfc57b66` |
| Runtime conversacional | [Ollama v0.40.15](https://github.com/ollama/ollama/releases/tag/v0.40.15), MIT | portátil Windows x64; SHA256 `9ca74a7ab66ec86870b1eecac7df160df08dc4e3a291ab5d2d59bcf9310221c6` verificado |
| Perfil CPU | [Qwen2.5 1.5B Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct), Apache 2.0 | `qwen2.5:1.5b`, ID local `65ec06548149`, 986 MB |
| Perfil GPU usado en este equipo | [Qwen3 4B Instruct 2507](https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507), Apache 2.0 | `qwen3:4b-instruct-2507-q4_K_M`, ID `0edcdef34593`, 2.5 GB |
| Postura | [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/python), biblioteca Apache 2.0; bundle oficial Google | `pose_landmarker_lite/float16/1`; SHA256 `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a` |
| Voz de salida | Windows SAPI para la prueba; SpeechSynthesis con `localService=true` y español para la UI | Solo voces locales instaladas; sin alternativa cloud si no hay una |

Los pesos no se versionan ni se redistribuyen. El bundle MediaPipe se obtiene de la
fuente oficial y su manifest conserva URL/hash; antes de redistribuir pesos, revisar
los términos del recurso concreto, distintos de la licencia de la biblioteca.

## Hardware observado y requisitos

Windows 11 Home; Intel i7-13650HX, 14 núcleos/20 hilos; unos 24 GB RAM; RTX 4050
Laptop. `nvidia-smi` confirmó **6141 MiB de VRAM**. Durante una inferencia se observaron
3942 MiB utilizados y 76% GPU; esa observación no es un máximo ni una prueba de carga.

Guía de dimensionamiento, no mínimos certificados en otros equipos:

- CPU: Python 3.13, Java 21, Node 24, al menos 16 GB RAM recomendados para Windows y
  los cinco procesos, 4 hilos para inferencia y unos 10 GB libres para instalación/cachés.
- GPU: 24 GB RAM y NVIDIA con 6 GB VRAM recomendados para el perfil 4B probado.
  Se utilizó el runtime portátil con sus bibliotecas CUDA; no se requiere PyTorch.
- Mantener espacio adicional para grabaciones y backups. Una sesión admite 15 MiB.

Al comienzo había solo unos 3.4 GiB libres de RAM; las cifras dependen de otras apps.
No se asegura la misma latencia en otro CPU o portátil con límites térmicos distintos.

## Configuración

Solo un adaptador Ollama y un adaptador faster-whisper. Elegir el perfil
explícitamente; no hay cambio silencioso de proveedor o descarga durante inferencia.

| Variable | Default | Efecto |
|---|---|---|
| WHISPER_MODEL_PATH | `ai-service/models/whisper-base` | ruta local con model.bin/tokenizer/config |
| WHISPER_DEVICE / WHISPER_COMPUTE_TYPE | cpu / int8 | CPU independiente del LLM |
| INFERENCE_THREADS | 4 | límite de hilos locales |
| OLLAMA_URL | `http://127.0.0.1:11434` | solo HTTP de loopback, sin proxy/redirecciones |
| OLLAMA_MODEL | `qwen2.5:1.5b` | perfil CPU comprobado |
| LLM_NUM_GPU | 0 | 0=CPU; 99 permite descargar todas las capas a la GPU si caben |
| LLM_CONTEXT_TOKENS | 4096 | contexto acotado |
| LLM_TIMEOUT_SECONDS | 240 | timeout de llamada local; Spring debe tener margen (300) |
| POSE_MODEL_PATH | `ai-service/models/pose_landmarker_lite.task` | bundle local |
| INTERNAL_API_KEY | vacío | fallo cerrado si falta; compartir solo entre Spring y FastAPI |

La configuración local de este equipo utiliza 4B y LLM_NUM_GPU=99. El archivo
`.env.example` conserva el perfil CPU para facilitar otras instalaciones.

Una sola generación LLM y una transcripción por instancia se ejecutan simultáneamente;
el exceso devuelve 429 explícito. FastAPI usa hilos para los modelos, dejando libre
el event loop. Usar un único worker Uvicorn para evitar duplicar pesos en RAM/VRAM.

## Verificación y límites

Con prompts iniciales, el modelo 1.5B produjo una respuesta fuera de rol y un JSON
incompleto. Se simplificó el prompt y se hicieron obligatorios los cuatro criterios
por claves fijas. La nueva prueba completa en CPU aprobó transcripción, evaluación,
respuesta, feedback y ausencia de persona. Esto acredita el contrato y la inferencia,
no la precisión académica de una nota subjetiva.

4B en CPU resultó muy lento (aproximadamente 2 tokens/s en esa medición) y se interrumpió
la prueba; no se presenta como configuración recomendada. La primera inicialización
CUDA agotó un timeout de 120 s. El perfil final usa más margen y la prueba real 4B/GPU
aprobó los cinco componentes. Ver tiempos y evidencias en el informe de pruebas.

No se ha validado la precisión postural con personas reales, la robustez frente a
todos los acentos ni la calidad de todas las conversaciones posibles. Los modelos
pueden equivocarse aun devolviendo JSON válido. El evaluador muestra fuentes,
justificaciones y limitaciones, y requiere revisión humana.

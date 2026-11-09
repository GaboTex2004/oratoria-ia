# Contratos HTTP reales

Actualización de suscripciones: GET /plans agrega price, currency e interval sin
eliminar entitlements. [Contratos completos de checkout, consumo, cancelación y
simulación administrativa](saas/FLUJO_CHECKOUT.md). No existe pasarela bancaria.

Base pública local: `http://localhost:8081/api`. Salvo registro, login y test, se
requiere `Authorization: Bearer <JWT>`. El usuario se obtiene del Principal, nunca de
un userId enviado por el cliente. Las rutas ADMIN exigen rol ADMIN en servidor.

## Spring Boot

| Método y ruta relativa | Entrada | Respuesta / éxito |
|---|---|---|
| GET `/test` | — | texto, 200; disponibilidad HTTP, no certifica modelos |
| POST `/auth/register` | firstName, lastName, email, password | token + UserResponse, 201 |
| POST `/auth/login` | email, password | token + UserResponse, 200 |
| GET `/users/me` | JWT | id, nombres, email, role, plan; 200 |
| PUT `/users/me` | firstName, lastName | perfil actualizado, 200 |
| POST `/users/me/password` | currentPassword, newPassword | 204; revoca todos los tokens previos |
| GET `/exercises` | trainingType opcional | catálogo activo, 200; categoría/nivel/objetivo/criterios |
| GET `/exercises/{id}` | UUID | ejercicio activo, 200 |
| POST `/sessions` | exerciseId o trainingType | sesión RECORDING, 201; aplica cuota |
| GET `/sessions` | — | sesiones propias, 200 |
| GET `/sessions/{id}` | UUID | sesión propia, 200 |
| POST `/sessions/{id}/complete` | multipart: audio, durationSeconds | sesión finalizada, 200; encola análisis |
| GET `/sessions/{id}/audio` | — | audio privado, 200, Cache-Control no-store |
| GET `/sessions/{id}/analysis` | — | estado, transcripción, segmentos, palabras, métricas; 200 |
| POST `/sessions/{id}/analysis/retry` | — | estado actualizado, 200 |
| GET `/sessions/{id}/evaluation` | — | estado, rúbrica, criterios, recomendaciones; 200 |
| POST `/sessions/{id}/evaluation/retry` | — | estado actualizado, 200 |
| GET `/sessions/{id}/body-analysis` | — | último análisis corporal; 200 o 404 si no existe |
| POST `/sessions/{id}/body-analysis` | consent=true, frames: base64 JPEG/PNG[] | indicadores observables, 200 |
| DELETE `/sessions/{id}/data` | — | 202; limpia resultados y encola borrado físico |
| GET `/progress/summary` | — | overview, history, criteriaEvolution, insights, subscription; 200 |
| GET `/recommendations` | — | regla, categoría, nivel, razón y ejercicios reales; 200 |
| GET `/plans` | — | planes y derechos configurados; 200 |
| GET `/simulations/scenarios` | — | escenarios y dificultades, 200 |
| POST `/simulations` | scenarioId, difficulty | simulación y apertura del escenario, 201 |
| GET `/simulations` | — | historial propio, 200 |
| GET `/simulations/{id}` | — | sesión, turnos y feedback, 200 |
| POST `/simulations/{id}/respond` | Idempotency-Key; multipart audio, durationSeconds | turno persistido + respuesta local, 200 |
| POST `/simulations/{id}/finish` | — | simulación finalizada y evaluada, 200 |
| GET `/simulations/{id}/feedback` | — | evaluación final, 200 |
| GET `/simulations/{id}/turns/{turnId}/audio` | — | audio privado, 200 |
| DELETE `/simulations/{id}/data` | — | 202; limpia conversaciones y encola audios |
| GET `/admin/status` | ADMIN | confirmación de rol, 200 |
| GET `/admin/overview` | ADMIN | conteos reales de usuarios, ejercicios y sesiones, 200 |
| GET `/admin/users?page=0` | ADMIN | página de 50 cuentas, sin hashes, 200 |
| PUT `/admin/users/{id}` | ADMIN; enabled, plan | 204; no deshabilita ADMIN |
| POST `/admin/users/{id}/password` | ADMIN; newPassword (12–72 caracteres y <=72 bytes) | 204; solo cuentas USER |
| GET `/admin/exercises` | ADMIN | catálogo completo para edición, 200 |
| POST `/admin/exercises` | ADMIN; ExerciseUpdate | 201 |
| PUT `/admin/exercises/{id}` | ADMIN; ExerciseUpdate | 204; conserva código y modalidad históricos |

`ExerciseUpdate`: code (`[A-Z0-9_]{3,60}`), title (120), description (400),
prompt (1000), objective (400), category, difficulty, duration (15–300), trainingType,
active. Categorías: CLARIDAD, RITMO, ENTONACION, FLUIDEZ, ESTRUCTURA, ARGUMENTACION,
IMPROVISACION, NO_VERBAL, ESTILOS. Dificultades: BASIC, INTERMEDIATE, ADVANCED.

Ejemplos sin credenciales reales:

```json
{"firstName":"Nombre","lastName":"Apellido","email":"persona@example.test","password":"<CONTRASEÑA_PRIVADA>"}
```

```json
{"trainingType":"PRESENTATION"}
```

```json
{"scenarioId":"job-interview","difficulty":"BASIC"}
```

El informe PDF no tiene un endpoint duplicado: `/reports/{id}` compone los tres
contratos de sesión/análisis/evaluación, y el navegador imprime o guarda PDF localmente.

## FastAPI interno

Base: `http://127.0.0.1:8000`. OpenAPI real: `/openapi.json`, interfaz `/docs`.
No enviar llamadas internas directamente desde React ni guardar la clave en VITE_*.

| Método / ruta | Entrada | Resultado |
|---|---|---|
| GET `/`, `/health` | pública | 200; proceso HTTP disponible, no certifica pesos cargados |
| POST `/api/v1/speech/analyze` | clave; multipart file/audio + session_id/duration_seconds, o cuerpo binario con X-Session-Id, X-Audio-Duration-Seconds y Content-Type | SpeechAnalysisResponseSchema |
| POST `/api/v1/evaluation/evaluate` | clave; sessionId, trainingType, exerciseTitle/Prompt opcionales, transcriptText, metrics | SpeechEvaluationResponseSchema |
| POST `/api/v1/simulation/reply` | clave; escenario/rol/contexto/objetivo, dificultad, history, userTranscript, turnNumber/maxTurns | replyText, intent, shouldEnd, focus, provider, model |
| POST `/api/v1/simulation/feedback` | clave; escenario/contexto/objetivo, history | sufficientEvidence, overallScore, criteriaScores, feedback |
| POST `/api/v1/pose/analyze` | clave; frames[] (1–12 base64, <=600000 caracteres por frame) | presencia, hombros, desplazamiento de cabeza y movimiento |

Cabeceras internas: `X-Internal-Api-Key`, `X-Request-Id` opcional (normalizado).
Máximo global de cuerpo: límite de audio + 64 KiB de overhead. La clave se valida
antes de analizar el JSON o multipart. Audio: máximo 15 MiB y duración decodificada
de 300 s. Imágenes: JPEG/PNG hasta 1920×1080; dimensiones verificadas antes de decodificar.

## Estados y errores

Análisis/evaluación: PENDING → PROCESSING → COMPLETED o FAILED. La interfaz consulta
el estado y permite reintentos. El trabajo se despacha después del commit de la
sesión; el procesamiento de modelos no mantiene abierta la transacción de práctica.

Spring: 400 validación/regla de negocio/cuota, 401 sin sesión, 403 rol/propietario,
404 recurso ausente, 413 subida excesiva, 500 error interno con mensaje genérico.
Formato `ApiError`: timestamp, status, error, message, path, fieldErrors.

FastAPI: 401 clave interna, 400 audio/imagen inválidos, 413 tamaño, 422 esquema,
429 motor ocupado, 502 JSON/criterios inválidos, 503 runtime o modelo no disponible,
504 timeout. Errores de proveedor incluyen code, message, retryable, origin y
requestId donde corresponde. Los detalles no contienen claves ni transcripciones.

No hay fallback cloud ni descarga implícita de pesos. Un modelo ausente devuelve un
error de configuración; preparar los pesos y reintentar es una acción explícita.

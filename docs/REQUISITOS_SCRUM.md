# Backlog y trazabilidad — propuesta de continuidad Software II

Responsable de la rama: Isaías David Gutierrez Teran. Rama: `isaias-gutierrez`.
El 2026-11-08 el responsable autorizó definir 24 HU nuevas al no disponer del
documento original. **No son una transcripción ni certificación de las HU de Software I.**
Se conserva el documento histórico `ESTADO_PROYECTO_ORATORIAAI.md` y el historial Git.

Los códigos CU de esta matriz son referencias de interacción propuestas, no un
reemplazo silencioso de identificadores originales. El backlog se gestiona por HU.
No se inventan Daily, fechas de ejecución de sprints, retrospectivas ni contribuciones.

## Historias y criterios de aceptación

| Sprint | HU / requisito / CU propuesto | Como usuario quiero… | Criterio de aceptación observable |
|---|---|---|---|
| 1 | HU-01 / R-01 / CU-01 | crear una cuenta | Email normalizado, contraseña BCrypt, rol USER y plan FREE; rechaza duplicados |
| 1 | HU-02 / R-02 / CU-02 | entrar y salir de mi cuenta | JWT válido permite acceso, credencial incorrecta se rechaza, logout elimina token local |
| 1 | HU-03 / R-03 / CU-03 | editar mi perfil | Nombres se guardan y persisten tras recargar; no se puede cambiar rol desde el perfil |
| 1 | HU-04 / R-04 / CU-04 | cambiar o recuperar mi contraseña | Comprueba la contraseña actual; recuperación local solo por ADMIN; revoca tokens anteriores |
| 1 | HU-05 / R-05 / CU-05 | consultar ejercicios por habilidad y nivel | Muestra catálogo persistido, objetivo, consigna, duración, modalidad y nivel |
| 1 | HU-06 / R-06 / CU-06 | preparar una demostración segura | Seed idempotente y cuentas ADMIN/FREE/PREMIUM solo en perfil dev, sin claves fijas |
| 2 | HU-07 / R-07 / CU-07 | iniciar y finalizar una práctica | Sesión pertenece al usuario y guarda estado/fechas/duración |
| 2 | HU-08 / R-08 / CU-08 | grabar y escuchar mi intervención | Permiso explícito, límites de tamaño/duración y descarga autenticada por propietario |
| 2 | HU-09 / R-09 / CU-09 | obtener una transcripción local | Whisper procesa español offline con segmentos y palabras temporizadas |
| 2 | HU-10 / R-10 / CU-10 | consultar indicadores de voz | WPM, pausas, muletillas, RMS, dBFS relativo y F0 con valores ausentes explícitos |
| 2 | HU-11 / R-11 / CU-11 | evaluar mi discurso con una rúbrica | Cuatro criterios completos, reglas propias + semántica local; rechaza puntuaciones ausentes |
| 2 | HU-12 / R-12 / CU-12 | consultar retroalimentación persistida | Fortalezas, mejoras y acciones; sin puntuación con evidencia insuficiente |
| 3 | HU-13 / R-13 / CU-13 | elegir un escenario conversacional | Ocho escenarios, rol/contexto/objetivo y dificultad |
| 3 | HU-14 / R-14 / CU-14 | conversar varios turnos con IA local | Conserva contexto y evita duplicados mediante Idempotency-Key |
| 3 | HU-15 / R-15 / CU-15 | finalizar y evaluar una simulación | Dos respuestas mínimas para puntuar; nota separada de prácticas; voz opcional local |
| 3 | HU-16 / R-16 / CU-16 | improvisar sobre un tema inesperado | Sorteo de catálogo real, temporizador, grabación y evaluación del discurso |
| 3 | HU-17 / R-17 / CU-17 | practicar distintos estilos | Ejercicios narrativos y persuasivos además de modalidades académicas; sin afirmar un clasificador aprendido |
| 3 | HU-18 / R-18 / CU-18 | observar postura y movimiento | Consentimiento previo, cámara apagable, indicadores 2D locales y ausencia de persona sin métricas ficticias |
| 4 | HU-19 / R-19 / CU-19 | recibir una siguiente práctica adaptada | Selección basada en criterio reciente más débil y regla de dificultad documentada |
| 4 | HU-20 / R-20 / CU-20 | revisar historial y evolución | Gráficas y comparaciones usan solo evaluaciones y métricas reales válidas |
| 4 | HU-21 / R-21 / CU-21 | conocer mi plan y sus cuotas | FREE 5 prácticas/1 simulación por mes UTC, PREMIUM ilimitado; sin cobros |
| 4 | HU-22 / R-22 / CU-22 | administrar cuentas y ejercicios | Solo ADMIN puede habilitar usuarios, asignar planes, restablecer USER y editar/desactivar ejercicios |
| 4 | HU-23 / R-23 / CU-23 | exportar un informe individual | Informe autenticado con datos reales, formato de impresión y guardado PDF del navegador |
| 4 | HU-24 / R-24 / CU-24 | controlar mis datos privados | Elimina audio, texto y resultados; conserva solo metadatos de cuota; propietario ajeno rechazado |

## Matriz requisito → HU → interacción → módulo → endpoint → entidad → prueba

| R/HU/CU | Módulo / pantalla | Endpoint principal | Entidad o fuente | Prueba / evidencia |
|---|---|---|---|---|
| 01 | auth `/register` | POST `/api/auth/register` | app_users, subscriptions | AuthIntegrationTests; recorrido navegador |
| 02 | auth `/login` | POST `/api/auth/login`, GET `/api/users/me` | app_users | AuthIntegrationTests, JWT filter |
| 03 | user `/profile` | PUT `/api/users/me` | app_users | LocalCapabilitiesIntegrationTests |
| 04 | user/admin | POST `/api/users/me/password`, `/api/admin/users/{id}/password` | app_users.token_version | Pruebas de cambio, recuperación y revocación |
| 05 | exercise `/training` | GET `/api/exercises` | exercises | SessionAndExerciseIntegrationTests, responsive |
| 06 | inicializadores | Arranque `dev` | plans, plan_entitlements, app_users, subscriptions | Idempotencia de catálogos y DemoUserInitializer |
| 07 | session `/training` | POST `/api/sessions`, `/{id}/complete` | training_sessions | SessionAndExerciseIntegrationTests |
| 08 | session/audio | GET `/api/sessions/{id}/audio` | almacenamiento local, training_sessions | Privacidad y ciclo completo; navegador sintético |
| 09 | speech | Interno POST `/api/v1/speech/analyze` | speech_analyses | verify_models; prueba real con WAV sintético |
| 10 | speech/voice | GET `/api/sessions/{id}/analysis` | speech_analyses.acoustic_metrics_json | test_local_inference, test_speech_analysis |
| 11 | evaluation | Interno POST `/api/v1/evaluation/evaluate` | speech_evaluations | test_speech_evaluation; test de criterio ausente |
| 12 | evaluation `/results` | GET `/api/sessions/{id}/evaluation` | speech_evaluations | SpeechEvaluationIntegrationTests, AsyncProcessingHotfixIntegrationTests |
| 13 | simulation | GET `/api/simulations/scenarios` | SimulationScenarioCatalog | SimulationDomainTests |
| 14 | simulation `/simulations` | POST `/api/simulations/{id}/respond` | simulation_sessions, simulation_turns | SimulationIntegrationTests; modelos locales reales |
| 15 | simulation | POST `/api/simulations/{id}/finish` | final_feedback_json | SimulationIntegrationTests y verificación Ollama |
| 16 | exercise | Catálogo IMPROVISATION + sesiones | exercises, training_sessions | Catálogo y recorrido compartido de práctica |
| 17 | exercise | Catálogo categoría ESTILOS | exercises | Seed y administración; evaluación semántica general |
| 18 | body `/body` | POST/GET `/api/sessions/{id}/body-analysis` | body_analyses | Permisos/consentimiento; MediaPipe con imagen vacía; falta persona real |
| 19 | progress `/recommendations` | GET `/api/recommendations` | progreso calculado, exercises | Responsive/API; reglas v1 sin modelo supervisado |
| 20 | progress `/progress` | GET `/api/progress/summary` | sesiones/análisis/evaluaciones | ProgressCalculatorTests, ProgressServiceTests, ProgressIntegrationTests |
| 21 | subscription `/plans` | GET `/api/plans`; creación de sesión/simulación | plans, plan_entitlements, subscriptions | SubscriptionIntegrationTests; rechazo de sexta sesión FREE |
| 22 | admin `/admin` | GET/PUT `/api/admin/users`, POST/PUT `/api/admin/exercises` | app_users, subscriptions, exercises | LocalCapabilitiesIntegrationTests; navegador administrativo |
| 23 | training `/reports/{id}` | GET de sesión/análisis/evaluación | datos persistidos; impresión local | PDF real del recorrido automatizado; no servidor PDF separado |
| 24 | privacidad | DELETE `/api/sessions/{id}/data`, `/api/simulations/{id}/data` | media_deletion_jobs y agregados | Pruebas de aislamiento/borrado y cola de archivos |

## Definition of Done y límites

Implementación, compilación, pruebas ejecutadas, manejo de ausencia/error, privacidad,
documentación y evidencia real. Un mock no acredita inferencia; una imagen negra no
acredita precisión postural con personas; una nota del LLM no acredita validez académica.
El estado final verificado de cada HU y sprint está en `ENTREGA_FINAL_ORATORIAAI.md`.

No se considera terminado un clasificador propio, una validación clínica, una
recuperación por email o cobros: no se implementaron ni se requieren para los criterios
acordados arriba. Un alcance docente diferente debe registrarse como revisión del backlog.

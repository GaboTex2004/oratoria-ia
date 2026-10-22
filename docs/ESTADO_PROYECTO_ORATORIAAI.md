# OratoriaAI — Estado del proyecto y guía de continuidad

**Fecha:** 21 de octubre de 2026  
**Proyecto:** Ingeniería de Software I — OratoriaAI  
**Propósito:** documento de traspaso para que otro integrante pueda continuar el desarrollo sin perder el contexto.

> Este documento se basa en los informes compartidos de Antigravity CLI y las pruebas manuales relatadas. No representa una auditoría independiente del último código ni certifica que todos los servicios estén funcionando ahora mismo.

## 1. Resumen ejecutivo

OratoriaAI es una plataforma web para practicar discursos y mejorar la comunicación oral. Permite registrar usuarios, seleccionar ejercicios, grabar y escuchar audios, guardar sesiones y analizar el habla mediante servicios de IA. La arquitectura existente se conserva: **React + TypeScript + Vite**, **Spring Boot (Java)**, **PostgreSQL** y **FastAPI (Python)**, con **Groq API** como proveedor de transcripción y evaluación semántica.

**Situación actual:** fases 0, 0.1, 1, 2 y **3 implementadas**. El hotfix de fase 3 corrigió la condición de carrera que dejaba análisis en `PENDING`; Antigravity reportó **26 pruebas backend, 14 de FastAPI y 9 de frontend aprobadas**. Queda aconsejada una comprobación manual de una grabación nueva y su evaluación completa en la laptop antes de integrar a `main`.

## 2. Avance por fases

| Fase | Objetivo | Estado actual |
|---|---|---|
| 0 — Auditoría | Inventario de arquitectura, dependencias y errores | Completada |
| 0.1 — Estabilización | Seguridad, manejo de errores, planes, entorno y pruebas | Completada según informe: 11 pruebas backend y 2 FastAPI, compilación frontend correcta |
| 1 — Ejercicios y sesiones | Catálogo, grabadora web, audio privado, historial | Implementada; 15 pruebas backend y 4 frontend aprobadas en el informe; grabación y reproducción probadas manualmente |
| 2 — Análisis de voz | Groq STT, transcripción, velocidad de habla, muletillas, pausas, resultados | Implementada; se obtuvo una pantalla real de transcripción y métricas en español |
| 3 — Evaluación del discurso | Rúbricas por ejercicio, IA semántica, recomendaciones y persistencia | **Implementada y hotfix completado**, con regresión automatizada aprobada; prueba manual completa aconsejada |
| 4 — Progreso y dashboard | Historial analítico, gráficas y métricas reales agregadas | Pendiente |
| 5 — Simulaciones IA | Entrevistas, debates y conversaciones interactivas | Pendiente |
| 6 — Análisis corporal | Postura, gestos y orientación de cabeza | Pendiente |
| 7 — Personalización y suscripciones | Aprendizaje adaptativo y límites de planes | Pendiente (existe base de FREE/PREMIUM) |
| 8 — Calidad y entrega | Pruebas finales, seguridad, despliegue y documentación | Pendiente |

## 3. Funciones implementadas según los informes

### Frontend
- Registro, inicio de sesión, rutas protegidas, dashboard inicial.
- Catálogo de **7 tipos de entrenamiento** (presentación, entrevista, debate, ventas, defensa de proyecto, pitch e improvisación).
- Grabación de audio desde el navegador mediante `MediaRecorder`.
- Sección **Mis sesiones**, reproductor de audio y controles de carga/error corregidos durante fase 2.
- Sección **Resultados** con transcripción en español y métricas básicas.
- Interfaz de evaluación semántica en fase 3, con estados de carga, controles de reintento y recuperación; conviene comprobar manualmente el flujo completo.

### Backend Spring Boot
- Autenticación JWT, BCrypt y autorización por roles/propietario.
- API de ejercicios y sesiones, carga/descarga privada de audio.
- Registro de sesiones en PostgreSQL; archivos de audio fuera de la base de datos.
- Orquestación de análisis de voz y evaluación discursiva; resultados en `speech_analyses` y `speech_evaluations` según fase 3.
- Inicialización de planes FREE y PREMIUM y pruebas automatizadas.

### Servicio de IA FastAPI
- Integración con Groq para reconocimiento de voz mediante modelo Whisper configurable.
- Cálculo de métricas verbales a partir de transcripción/marcas temporales, con estimaciones cuando corresponde.
- Evaluación semántica mediante Groq LLM y rúbricas por tipo de entrenamiento, según implementación reportada de fase 3.
- Comunicación interna autenticada con Spring mediante `INTERNAL_API_KEY`.

## 4. Última evidencia funcional observada

La captura compartida mostró una sesión de alrededor de 9 segundos, transcrita al español con métricas: **16 palabras, 101.9 WPM, 8.5 s de tiempo hablado, 0 muletillas y 0 pausas prolongadas detectadas**. Esto verifica visualmente el análisis de voz para **esa sesión**, no el funcionamiento de todas las rutas ni de la evaluación semántica nueva.

## 5. Incidencia de fase 3 resuelta y seguimiento

**Incidencia diagnosticada:** al completar una sesión, `TrainingSessionService.completeSession` lanzaba un `CompletableFuture.runAsync` dentro de una transacción `@Transactional`, **antes del COMMIT**. El worker consultaba la nueva fila `speech_analyses`, todavía invisible bajo `READ COMMITTED`, y terminaba sin procesarla. Resultado: sesiones estancadas en `PENDING` sin error.

**Correcciones reportadas por Antigravity:**
- Despacho de análisis y evaluación mediante `TransactionSynchronization.afterCommit()`.
- Recuperación de `PENDING` sin worker activo y tratamiento de `PROCESSING` interrumpido.
- Propagación de errores de transcripción hacia evaluaciones dependientes.
- Reintentos controlados para fallos de autenticación interna o reinicios, preservando historial de intentos.
- Mayor observabilidad: tiempos, IDs y errores estructurados, sin imprimir secretos.
- Pantallas de resultados con estados separados para transcripción y evaluación; aviso de procesamiento lento y controles de reintento.

**Verificación reportada:** 26/26 pruebas de backend, 14/14 de ai-service y 9/9 de frontend, además de lint y build. Incluye prueba de integración asíncrona post-commit. Son resultados del informe de Antigravity; **aún conviene repetir manualmente una sesión completa en el equipo y registrar su resultado**.

**Seguridad:** el valor de prueba `INTERNAL_API_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` es predecible. Sustituirlo en ambos `.env` por **la misma clave aleatoria robusta**, sin compartirla ni versionarla. Verificar que ambos servicios cargan sus variables de entorno.

**Validación manual recomendada antes del merge:**
1. Reiniciar Spring Boot y verificar FastAPI.
2. Grabar una intervención en español de 15 segundos o más desde `/training`.
3. Confirmar que análisis y evaluación llegan a `COMPLETED` y muestran resultados en `/results`.
4. Recargar la página, comprobar persistencia y probar el reintento de una sesión antigua `FAILED` si corresponde.
5. Comprobar que el acceso a audios y resultados de otros usuarios esté denegado.

## 6. Cómo ejecutar localmente (Windows / PowerShell)

Abrir cuatro terminales **desde la raíz del repositorio**.

**Terminal 1 — PostgreSQL (Docker Desktop debe estar abierto):**

```powershell
docker compose up -d
docker compose ps
```

**Terminal 2 — Backend:**

```powershell
cd backend
# Verificar que las variables necesarias se carguen desde .env o desde el entorno.
.\mvnw.cmd spring-boot:run
```

**Terminal 3 — FastAPI:**

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --env-file .env --host 127.0.0.1 --port 8000
```

**Terminal 4 — React:**

```powershell
cd frontend
npm run dev
```

URLs locales: `http://localhost:5173`, `http://localhost:8081/api/test`, `http://localhost:8000/health`. Si Spring no importa el `.env` en la versión actual, inyectar variables de entorno en la sesión PowerShell antes de ejecutarlo.

## 7. Verificaciones automatizadas

```powershell
# Desde backend/
.\mvnw.cmd test

# Desde frontend/
npm test
npm run lint
npm run build

# Desde ai-service/
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Último resultado reportado (hotfix fase 3): **backend 26/26, ai-service 14/14, frontend 9/9**, más ESLint y build exitosos. Repetir las pruebas antes del PR si hay cambios posteriores.

## 8. Precauciones de colaboración

- No subir `.env`, claves, tokens, grabaciones privadas, carpetas `data/audio`, `.venv`, `node_modules` ni artefactos de compilación.
- Mantener claves fuera del repositorio; actualizar solamente archivos `.env.example` con valores ficticios.
- Usar ramas por funcionalidad y Pull Requests hacia `main`.
- Antes de fusionar, revisar `git diff`, pruebas y archivos cambiados; no usar `git push --force`.
- No usar `docker compose down -v` salvo que se quiera borrar deliberadamente la base de datos local.
- Fase 3 y su hotfix se consideran implementados según el informe; antes de fusionar, realizar prueba manual de transcripción + evaluación semántica completa en la laptop.

## 9. Próximo hito

**Siguiente fase: Fase 4 — Dashboard y progreso real.** Antes, completar una prueba manual de punta a punta con la versión corregida, guardar evidencia y abrir Pull Request a `main` después de revisión del equipo.

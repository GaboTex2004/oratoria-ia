# Entrega local OratoriaAI — continuidad Software II

Fecha: 8 de noviembre de 2026. Responsable de rama: IsaiasGutierrezTeran según la
configuración Git existente. Rama `isaias-gutierrez`, base `2784c2e`; respaldo
`respaldo/isaias-antes-finalizacion-20261108`. Esta finalización **no se publicó**.
Se conserva el historial y la autoría previa; los reportes históricos permanecen
identificados como tales. No se declara un proyecto al 100 % ni validación científica.

## 1. Resumen

Se extendió la plataforma existente sin sustituir React, Spring, PostgreSQL ni
FastAPI. Se reemplazó la inferencia remota por modelos locales, completando perfil,
administración, cuotas, privacidad, recomendaciones, cámara e informes. El sistema
funciona localmente y cuenta con pruebas ejecutadas, incluyendo un recorrido de
navegador con los modelos reales. Las grabaciones de prueba son sintéticas.

## 2. Arquitectura final

React → API Spring con JWT/propiedad/roles → PostgreSQL y servicio interno FastAPI.
FastAPI usa Whisper, MediaPipe y Ollama/Qwen sobre loopback; nunca entrega la clave
interna al navegador. [Diagramas de componentes, clases y secuencia](ARQUITECTURA_FINAL.md).

## 3. Tecnologías y versiones verificadas

| Componente | Versión probada |
|---|---|
| Windows / Java | Windows 11 Home / JDK 21.0.1 |
| Spring Boot / Maven | 4.1.1 / wrapper 3.9.16 |
| PostgreSQL | 17.6 |
| Node / npm | 24.9.0 / 11.6.0 |
| React / React Router | 19.2.8 / 7.18.3 |
| TypeScript / Vite | 6.0.2 / 8.3.6 en lockfile |
| Python / FastAPI / Uvicorn | 3.13.7 / 0.162.2 / 0.54.0 |
| faster-whisper / NumPy / MediaPipe | 1.2.1 / 2.2.6 / 0.10.35 |
| Ollama / Playwright | 0.40.15 / 1.56.1 |

Dependencias directas fijadas y lockfiles incluidos. npm audit terminó con cero
avisos; esto no certifica ausencia de vulnerabilidades en todo el sistema.

## 4. Módulos implementados

Autenticación y renovación de contexto, perfil/contraseña, catálogo de 20 ejercicios,
prácticas con audio privado, transcripción, métricas acústicas y temporales,
evaluación con rúbrica, ocho escenarios conversacionales, improvisación, estilos
narrativos/persuasivos, recomendaciones por reglas, historial/comparación/progreso,
planes/cuotas, ADMIN, cámara opcional, informes PDF y eliminación de contenido.
No se implementó cobro ni recuperación automática por email.

## 5. Modelos locales utilizados

- Whisper base convertido por Systran: español con tiempos de segmentos/palabras.
- Qwen2.5 1.5B para CPU: evaluación y conversación verificadas.
- Qwen3 4B Instruct 2507 Q4_K_M para GPU: perfil activo en RTX 4050 Laptop 6 GB.
- MediaPipe Pose Lite: indicadores 2D observables y ausencia de persona.
- Voz española local del navegador/sistema, opcional; no Piper ni voz remota.

[Fuentes oficiales, licencias, hashes, hardware, perfiles y mediciones](ia/MODELOS_LOCALES.md).
No existe fallback a Groq, Gemini, OpenAI ni otro proveedor remoto de inferencia.

## 6. Dataset y procedencia

No se recolectó un dataset humano ni se entrenó/ajustó un modelo supervisado propio.
Se implementaron reglas pedagógicas versionadas y un pipeline para preparar datos
consentidos, con separación por hablante y validaciones de metadatos. Common Voice
y OpenSLR se revisan como candidatos de voz; no son etiquetas de calidad oratoria
ni se afirma que fueron descargados o usados para entrenamiento en esta entrega.
[Procedencia, consentimiento y protocolo](ia/DATASET_Y_PROCEDENCIA.md).

## 7. Usuarios de demostración

`scripts/Initialize-Local.ps1` genera configuración privada y contraseñas aleatorias
ADMIN/FREE/PREMIUM. Spring las inicializa solo con perfil dev y no modifica cuentas
existentes. Las claves se consultan localmente en `backend/.env`, nunca en Git o
este informe. El seed fue ejecutado y verificado como idempotente.

## 8. Base de datos y migraciones

Se conservó el volumen y el esquema previo. PostgreSQL Docker usa 127.0.0.1:5435
porque 5434 pertenece a una instalación Windows existente. Flyway V1 baseline,
V2 campos/índices/métricas/cuerpo y V3 cola durable de borrado. Se probó adopción y
base inicialmente vacía. Hibernate valida, no modifica silenciosamente el esquema.
Las pruebas usan `oratoria_test`, separada de `oratoria_db`.
[Tablas, relaciones, privacidad y migración](BASE_DE_DATOS.md).

## 9. Endpoints

[API completa](API.md), incluyendo auth, users/me, ejercicios, sesiones/audio,
speech-analysis, evaluation, simulaciones/turnos/feedback, progreso, recomendaciones,
planes, ADMIN, body-analysis y DELETE de contenido. FastAPI recibe únicamente
llamadas internas autenticadas y acotadas; las rutas públicas de negocio son Java.

## 10. Pruebas ejecutadas

| Suite | Resultado final |
|---|---|
| Java/Spring/PostgreSQL | 49 aprobadas, cero fallos/errores/omitidas |
| Python sin pesos | 50 aprobadas; Ruff y formato aprobados |
| Frontend unitarias | 14 aprobadas; ESLint y build aprobados |
| Navegador Edge | 8 aprobadas en 2.6 min, incluida integración con modelos |
| Modelos CPU / GPU | Cinco comprobaciones aprobadas por perfil |
| Flyway base vacía | Una prueba de arranque aprobada, V1–V3 aplicadas |

[Plan reproducible](pruebas/PLAN_DE_PRUEBAS.md), [informe con fallos históricos](pruebas/INFORME_DE_PRUEBAS.md)
y [evidencia final del navegador](pruebas/evidencias/navegador-final.txt).

## 11. Resultados reales

El navegador ejecutó registro/login, grabación MediaRecorder con WAV sintético,
transcripción real Whisper, evaluación Qwen con rúbrica v2.0-local, simulación de
dos turnos y feedback, generación de PDF individual y de progreso, captura de
cámara ficticia, análisis Pose y borrado de contenido. El servicio corporal devolvió
200 tras corregir HTTP/1.1. Se probaron seis anchos entre 320 y 1440 sin desborde.

Los informes PDF y capturas proceden de esa ejecución, no de maquetas. Los JSON de
modelos CPU/GPU están en `pruebas/evidencias`; sus tiempos dependen de carga y caché.
La inferencia inicial GPU puede superar un minuto. Estas pruebas no miden el avance
pedagógico de un estudiante ni convierten una nota sintética en evidencia humana.

## 12. Cobertura obtenida

Hay cobertura funcional documentada por HU, pruebas de dominio e integración,
fronteras de seguridad, persistencia, cuotas y recorrido completo. **No se midió un
porcentaje de cobertura de líneas o ramas.** 49/50/14/8 son cantidades de tests,
no porcentajes de requisitos ni exactitud. No hay medición WER o acuerdo con docentes.

## 13. Problemas corregidos

Proveedores cloud, claves internas predeterminadas, tests contra base dev, falta
de migraciones, perfil incompleto, panel administrativo insuficiente, cuotas no
aplicadas, carencia de borrado de contenido, métricas acústicas ausentes, fuentes
remotas y avisos de dependencias JS. En integración: clases Java obsoletas, carga
de fuentes que dejaba página vacía, desborde móvil de iconos, selector accesible y
negociación h2c incompatible en el nuevo cliente corporal. Los fallos y correcciones
están detallados en el informe; no se ocultaron ni eliminaron suites para aprobar.

## 14. Limitaciones conocidas

Las rúbricas híbridas 70 % semántica/30 % reglas son provisionales y necesitan
calibración docente. F0/dBFS y postura 2D son aproximaciones, no diagnósticos.
Pose solo tiene validación técnica con imagen vacía/cámara ficticia; no exactitud
con personas reales. CPU 4B resultó demasiado lenta: usar 1.5B en ese perfil.
No hay pruebas de carga, móviles físicos, lector de pantalla o penetración completa.
El PDF depende de la función de impresión del navegador. La voz local puede no
estar instalada en otros equipos. Los flags comerciales históricos no equivalen
a funcionalidades pagadas; esta versión solo promete las cuotas documentadas.
No hay checkout, entrenamiento propio ni correo de recuperación. La eliminación
conserva metadatos mínimos de cuota y usa reintentos para el archivo físico.

## 15. Comandos para iniciar

Tras completar la preparación descrita en [README](../README.md):

```powershell
docker compose up -d --wait
# En terminales separadas desde la raiz:
.\scripts\Start-Ollama.ps1
.\scripts\Start-Ai.ps1
.\scripts\Start-Backend.ps1
# En otra terminal:
cd frontend
npm.cmd run dev -- --host 127.0.0.1
```

Abrir http://localhost:5173. No iniciar una segunda instancia si el puerto ya está
ocupado por estos servicios. Para pruebas: `scripts/Test-Local.ps1` con DB aislada.

## 16. Estado de las 24 HU / interacciones propuestas

El usuario autorizó formular HU nuevas porque no estaba el documento original.
Los CU de la matriz son referencias propuestas y no certifican las HU de Software I.
[Texto, aceptación y trazabilidad completas](REQUISITOS_SCRUM.md).

| HU | Estado técnico y evidencia |
|---|---|
| 01 Registro | Implementada; auth y recorrido navegador |
| 02 Login/roles | Implementada; JWT/roles y rutas protegidas |
| 03 Perfil | Implementada; persistencia y pruebas Java |
| 04 Contraseña/recuperación | Implementada en modo local administrado; revocación y límites probados |
| 05 Catálogo | Implementada; filtros, objetivo/nivel y 20 ejercicios |
| 06 Demo | Implementada; tres perfiles por entorno, idempotencia |
| 07 Práctica | Implementada; estados, propiedad y recorrido real |
| 08 Audio | Implementada; grabación sintética, acceso privado y límites |
| 09 Transcripción | Implementada; Whisper real en ambos perfiles |
| 10 Voz | Implementada; fórmulas y señales sintéticas verificadas, sin validación clínica |
| 11 Rúbrica | Implementada; reglas + Qwen real; calibración docente pendiente |
| 12 Feedback | Implementada; persistencia y rechazo de evidencia insuficiente |
| 13 Escenarios | Implementada; ocho escenarios y tests de catálogo |
| 14 Conversación | Implementada; dos turnos reales y protección de concurrencia/idempotencia |
| 15 Evaluación conversacional | Implementada; resultado separado; voz depende de voz local instalada |
| 16 Improvisación | Implementada con catálogo aleatorio; no generador de temas entrenado |
| 17 Estilos | Implementada mediante ejercicios narrativos/persuasivos; sin clasificador aprendido |
| 18 Corporal | Integración implementada/ejecutada; precisión con personas pendiente |
| 19 Adaptación | Implementada por reglas de criterio débil y dificultad; no aprendizaje online |
| 20 Progreso | Implementada; datos persistidos, comparación y recorrido |
| 21 Cuotas | Implementada; FREE/PREMIUM por sesiones creadas mensuales UTC |
| 22 ADMIN | Implementada; autorización Java y creación real desde navegador |
| 23 Informe | Implementada; dos PDF generados por navegador |
| 24 Privacidad | Implementada; borrado de contenido y cola durable, propietario verificado |

## 17. Estado de los cuatro sprints

Agrupación de backlog propuesta, **no actas de sprints ejecutados**. No se inventan
Daily, retrospectivas, horas ni fechas históricas.

| Sprint propuesto | HU | Estado de entrega |
|---|---|---|
| 1 Fundamentos | 01–06 | Implementación y pruebas técnicas completadas localmente |
| 2 Práctica y evaluación | 07–12 | Integración local verificada; validación de rúbrica pendiente |
| 3 Simulación y expresión | 13–18 | Integración local verificada; exactitud corporal humana pendiente |
| 4 Seguimiento y control | 19–24 | Implementación y pruebas técnicas completadas localmente |

## 18. Archivos principales modificados

Backend: seguridad/JWT, user/profile, admin, quota, privacy, body, catálogo,
recomendaciones, clientes locales y migraciones V1–V3. Python: proveedores locales,
infraestructura Ollama, acústica/NLP, evaluación, pose, límites y preparación de
datos. Frontend: rutas, nuevas pantallas, fuentes, estilos adaptables y E2E.
Operación: scripts PowerShell, plantillas privadas, Compose y CI. Documentación:
auditoría, arquitectura, API, BD, Scrum, IA, manuales y pruebas.
Inventario completo: [archivos modificados](pruebas/evidencias/archivos-modificados.txt).

## 19. Demostración

Iniciar servicios, abrir la interfaz y registrar una cuenta o usar FREE de dev.
Elegir ejercicio, grabar una intervención consentida, analizar y mostrar texto,
métricas, rúbrica y PDF. Hacer dos turnos en una simulación y revisar feedback.
Mostrar progreso, recomendaciones y cámara solo tras consentimiento explícito.
Entrar como ADMIN para enseñar usuarios/planes/catálogo. Finalmente borrar datos
de la práctica de demostración. Para repetir sin hardware usar el E2E sintético
documentado. No presentar las cuentas de prueba como usuarios de la universidad.

## 20. Pendientes que requieren intervención humana

Validar 24 HU propuestas con docente/product owner y compararlas con Software I si
aparece el original; revisar rúbrica y reunir datos con consentimiento; comprobar
postura con personas y dispositivos reales; acordar retención y recuperación de
identidad; decidir publicación/push y revisión de despliegue. No se publicaron
secretos, modelos ni datos privados. Los cambios están preparados para revisión
local; el repositorio remoto conserva su versión previa hasta una publicación autorizada.

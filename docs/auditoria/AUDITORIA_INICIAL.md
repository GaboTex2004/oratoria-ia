# Auditoría inicial — 8 de noviembre de 2026

## Evidencia y protección

Punto de partida: `2784c2e`, rama `isaias-gutierrez`, árbol limpio, remoto
`https://github.com/GaboTex2004/oratoria-ia.git`. Identidad configurada:
`IsaiasGutierrezTeran`; no se modificó. Respaldo local:
`respaldo/isaias-antes-finalizacion-20261108`. No se autoriza push en esta fase.

Se inventariaron los archivos versionados, historial y módulos Java, React y Python.
No hay migraciones ni Dockerfiles ni CI. No se encontró el documento funcional
original con los 24 CU/HU; los estados heredados del pedido no son evidencia de ejecución.

## Arquitectura y versiones verificadas

React 19.2.8, React Router 7.18.3, TypeScript 6.0.2, Vite 8.2.2 y ESLint 10;
Spring Boot 4.1.1, Java 21.0.1, Maven Wrapper 3.9.16, JJWT 0.12.6;
PostgreSQL 17 en Compose; Python 3.13.7 con FastAPI/Uvicorn.
`requirements.txt` inicial usa rangos, no versiones reproducibles.

React → Spring Boot → PostgreSQL y almacenamiento local de audio.
Spring → FastAPI mediante clave interna. FastAPI invoca Groq/Gemini:
esto incumple el nuevo requisito de inferencia exclusivamente local.

## Estado observado al inicio

| Módulo | Evidencia | Situación |
|---|---|---|
| Autenticación | JWT, BCrypt, filtro, registro/login y pruebas | Implementado; repetir integración |
| Ejercicios | Seed idempotente de siete ejercicios | Operativo, faltan categorías/niveles/objetivos |
| Sesiones | Creación, audio privado, finalización | Implementado con validación por propietario |
| Voz | Contrato, métricas temporales, adaptador Groq | Depende de API externa |
| Evaluación | Rúbricas, persistencia, recuperación asíncrona | Depende de API externa; completa scores ausentes con 70 |
| Simulaciones | Catálogo, turnos, idempotencia, feedback separado | Depende de Groq/Gemini |
| Dashboard/progreso | Consultas y gráficas de datos persistidos | Implementado; consumo mensual no definido |
| Perfil | Ruta con PlaceholderPage | Pendiente |
| Administración | Solo verificación de rol | Parcial |
| Planes | FREE/PREMIUM y derechos | Parcial; límites no aplicados |
| Corporal, adaptación, PDF | No implementación funcional | Pendiente |
| Improvisación | Un ejercicio fijo con grabadora | Parcial |

## Problemas y riesgos

- README/ARCHITECTURE dicen que FastAPI es un esqueleto: contradicen el código.
- Tests Java apuntan por defecto a la base de desarrollo; deben aislarse antes de ejecutarse.
- Credencial interna con default predecible; debug Python habilitado en desarrollo.
- Audio HTTP leído entero antes de autenticar; sin límite de streaming previo.
- Duración declarada no protege contra archivos cuya duración real sea mayor.
- Evaluación convierte criterios ausentes en 70 y agrega citas sin verificar su origen.
- No hay borrado de grabaciones desde la aplicación ni política de conservación explícita.
- Botones de planes/configuración sin acción; perfil y administración incompletos.
- Hibernate `update` sin migraciones versionadas: riesgo de cambios de esquema no auditados.
- Python sin lock, pytest ni Ruff declarados. No se actualizarán versiones indiscriminadamente.
- No se afirma que una dependencia sea vulnerable solo por ser antigua: falta un análisis de avisos.

## Hardware detectado

Windows 11 Home, Intel i7-13650HX (14 núcleos/20 hilos), 24 842 960 KiB RAM,
3 575 260 KiB libres al medir. RTX 4050 Laptop e Intel UHD. WMI informa
AdapterRAM limitado a 32 bits; no se toma ese valor como VRAM exacta.
Disco C: aproximadamente 34.7 GB libres; D: 889 GB libres.
Ollama y FFmpeg no estaban en PATH. Python funciona fuera del sandbox.

## Plan de corrección

1. Aislar pruebas y registrar línea base real.
2. Conservar contratos y sustituir inferencia externa por faster-whisper y Ollama local.
3. Validar modelos/respuestas sin completar puntuaciones inventadas; añadir límites de recursos.
4. Completar perfil, desarrollo seguro, catálogo, recomendaciones y administración incrementalmente.
5. Integrar corporal/reportes y conservación de datos con autorización de cámara/micrófono.
6. Ejecutar suites e integración real; documentar lo pendiente y la trazabilidad sin inventar CU/HU.

Resultados iniciales: Python unittest reporta **33 tests OK** (PowerShell devuelve 1
por salida stderr nativa; el resultado del runner es OK), frontend **14 tests OK**.
Resto de resultados se registra en `docs/pruebas/INFORME_DE_PRUEBAS.md`.

Actualización de cierre: el usuario autorizó 24 HU nuevas para Scrum al no estar
el documento original. Se distinguen de Software I en REQUISITOS_SCRUM.md. El
análisis npm inicial encontró 13 avisos (tres altos y diez moderados); actualizaciones
compatibles y un override de minimatch redujeron el informe final a cero.

# Mecanismos de seguimiento y control · Sprint 4

Formato de la sección 2.8 del documento grupal. Generado con datos reales del repositorio el 17/11/2026. Las filas «Por asignar» deben completarse en la Daily Scrum.

## 2.8.1. Reporte de tareas finalizadas

| ID de tarea | Sprint | Descripción | Responsable | Fecha de finalización | CU / HU relacionada | Evidencia | Validación |
|---|---|---|---|---|---|---|---|
| SP4-01 | Sprint 4 | Edición de perfil y preferencias (objetivo, nivel, habilidades prioritarias, tipos preferidos, meta semanal) | Santiago Arteaga | 17/11/2026 | CU-19 / HU4-01 | a8a5761, 2c9c824 · PreferencesIntegrationTests (5) | CP4-01: los cambios se validan y permanecen almacenados |
| SP4-02 | Sprint 4 | Persistencia de objetivos y habilidades prioritarias (migración V6, tabla user_training_preferences) | Santiago Arteaga | 17/11/2026 | CU-19 / HU4-01 | a8a5761 · V6__personalization.sql | Datos guardados y validados (máx. 3 habilidades, meta 1–14) |
| SP4-03 | Sprint 4 | Reglas de recomendación adaptativa v2 (últimas 5 evaluaciones, dificultades recurrentes, nivel progresivo) | Santiago Arteaga | 17/11/2026 | CU-20 / HU4-02 | e7c4152 · AdaptiveRecommendationEngineTests (8) | CP4-02: propone ejercicio según dificultades y explica el motivo |
| SP4-04 | Sprint 4 | Ejercicios recomendados desde el historial, objetivos y estilo; versión básica para FREE | Santiago Arteaga | 17/11/2026 | CU-20 / HU4-02 | e7c4152 · RecommendationIntegrationTests (2) | Recomendación se actualiza tras cada evaluación |
| SP4-05 | Sprint 4 | Catálogo de 4 estilos de comunicación con rasgos, guía y ritmo sugerido; 6 ejercicios nuevos | Santiago Arteaga | 17/11/2026 | CU-21 / HU4-03 | a8a5761 · StylesIntegrationTests (7) | Estilos sin imitar identidad ni voz de personas |
| SP4-06 | Sprint 4 | Integración de estilos con sesiones, recomendaciones y retroalimentación por estilo | Santiago Arteaga | 17/11/2026 | CU-21 / HU4-03 | a8a5761 · StyleFeedbackCalculatorTests (5) | CP4-03: la selección queda asociada al entrenamiento |
| SP4-07 | Sprint 4 | Vista de planes FREE y PREMIUM con funciones incluidas; funciones del plan en Mi suscripción | Santiago Arteaga | 17/11/2026 | CU-22 / HU4-04 | 2c9c824 · prueba en navegador | El usuario consulta plan, límites y funciones |
| SP4-08 | Sprint 4 | Aplicación de límites y entitlements en backend (cámara, improvisación, estilos) | Santiago Arteaga | 17/11/2026 | CU-22 / HU4-04 | 3c3a631 · PlanFeatureIntegrationTests (6) | CP4-04: permite o restringe según el plan |
| SP4-09 | Sprint 4 | Reporte de desempeño por periodo con exportación PDF | Santiago Arteaga | 17/11/2026 | CU-23 / HU4-05 | 19be5a5 · ReportIntegrationTests (4) | CP4-05: se genera un PDF legible del usuario |
| SP4-10 | Sprint 4 | Administración de usuarios, ejercicios y categorías | Santiago Arteaga | 17/11/2026 | CU-24 / HU4-06 | 8d73c89, a8c5f8f · AdminCategoryIntegrationTests | Solo ADMIN gestiona recursos |
| SP4-11 | Sprint 4 | Configuración administrativa de planes (precio, funciones y límites) | Santiago Arteaga | 17/11/2026 | CU-24 / HU4-06 | c743965, c17d56f · AdminPlanIntegrationTests | Cambios de límite aplicados en el momento |
| SP4-12 | Sprint 4 | Pruebas de autorización, límites y exportación | Santiago Arteaga | 17/11/2026 | CU-22 a CU-24 | Suite backend 165/165 · frontend 19/19 | CP4-06: USER rechazado, ADMIN autorizado |
| SP4-13 | Sprint 4 | Parámetros del sistema, estado de módulos y estadísticas de uso (fuera del backlog original) | Santiago Arteaga | 17/11/2026 | CU-24 / HU4-06 | b20f86b, 134bb9e, e29e139 | Admin consulta estado y estadísticas |
| SP4-14 | Sprint 4 | Bitácora de acciones (inicios de sesión, registros y acciones administrativas) | Santiago Arteaga | 17/11/2026 | CU-24 / HU4-06 | 3badf07, d719b70 · AuditIntegrationTests (6) | Se registra quién hizo qué y cuándo; filtros por usuario, acción y fecha |
| SP4-15 | Sprint 4 | Respaldos de la base de datos con descarga y restauración desde la app (con respaldo automático previo) | Santiago Arteaga | 17/11/2026 | CU-24 / R-11 | 3badf07, d719b70 · BackupIntegrationTests (9) | Restauración probada en navegador sobre datos reales |
| SP4-16 | Sprint 4 | Reportes administrativos de ejercicios completados y dificultades frecuentes (JSON y PDF) | Santiago Arteaga | 17/11/2026 | CU-24 / Reportes del sistema | 3badf07 · AdminReportIntegrationTests (4) | PDF descargable por periodo |

## 2.8.2. Reporte de tareas en proceso

| ID de tarea | Responsable | Avance | Trabajo pendiente | Impedimento | Acción de seguimiento | Fecha objetivo |
|---|---|---|---|---|---|---|
| TP-01 | Santiago Arteaga | 90 % (código terminado y probado) | Abrir Pull Request de las ramas santiago/admin y santiago/sprint4 e integrarlas a main | Requiere revisión y acuerdo del equipo | Presentar los cambios al equipo y al PO | 19/11/2026 |
| TP-02 | Santiago Arteaga | 95 % (revisión hecha; importante y menores corregidos) | Optimizar el cálculo del historial en reportes y feedback por estilo | Ninguno | Medir con datos reales antes de optimizar | 20/11/2026 |
| TP-03 | Por asignar | 0 % | Diagramas de lógica de negocio de CU-20, CU-22 y CU-24 (comunicación, secuencia, estados y tiempo) | Secciones vacías en el documento | Asignar responsable en la Daily | 19/11/2026 |
| TP-04 | Por asignar | 50 % (pruebas automatizadas listas) | Registrar resultado obtenido y estado de CP4-01 a CP4-06 en el documento | Falta ejecutar la versión integrada en main | Ejecutar las pruebas tras el merge | 20/11/2026 |
| TP-06 | Por asignar | 0 % | Actualizar el despliegue Docker con las migraciones V5 a V7 y la carpeta de respaldos | Depende de TP-01 | Probar docker compose tras el merge | 20/11/2026 |

## 2.8.3. Reporte de errores

| ID de defecto | Fecha | Módulo afectado | CU relacionado | Severidad | Descripción | Pasos para reproducir | Resultado esperado | Responsable | Estado |
|---|---|---|---|---|---|---|---|---|---|
| ERR-01 | 17/11/2026 | Backend / Frontend | CU-22, CU-24 | Alta | Se podía guardar el plan Premium con precio 0 vaciando el campo; rompía el checkout y las renovaciones | Admin › Planes › borrar precio Premium › Guardar | Rechazar precio ≤ 0 para Premium | Santiago Arteaga | Verificado |
| ERR-02 | 17/11/2026 | Backend | CU-24 | Alta | Desactivar el plan PREMIUM provocaba errores 500 en el cobro | PUT /api/admin/plans/PREMIUM con active=false | Los planes FREE y PREMIUM no pueden desactivarse | Santiago Arteaga | Verificado |
| ERR-03 | 17/11/2026 | Backend | CU-22 | Alta | Las funciones Premium estaban definidas pero no se aplicaban: FREE podía usar todo | Usuario FREE › Análisis corporal › Capturar | Responder 403 con aviso de plan Premium | Santiago Arteaga | Verificado |
| ERR-04 | 17/11/2026 | Frontend | CU-24 | Media | Con la categoría ESTRUCTURA inactiva, crear un ejercicio fallaba aunque la pantalla mostraba otra categoría | Desactivar ESTRUCTURA › Crear ejercicio sin tocar la categoría | Usar la categoría visible seleccionada | Santiago Arteaga | Verificado |
| ERR-05 | 17/11/2026 | Frontend | CU-24 | Media | Los grabadores tenían 300 s fijos e ignoraban la duración máxima configurada | Admin › Sistema › duración 60 s › grabar práctica | Respetar el parámetro del sistema | Santiago Arteaga | Verificado |
| ERR-06 | 17/11/2026 | Frontend | CU-24 | Baja | El mensaje «Guardado» quedaba visible junto al error de un guardado posterior | Guardar bien y luego guardar con error | Mostrar solo el error | Santiago Arteaga | Verificado |
| ERR-07 | 17/11/2026 | Frontend | CU-24 | Baja | El anuncio global no aparecía en Inicio ni en Progreso | Publicar anuncio › abrir Inicio | Mostrar el anuncio en todas las pantallas | Santiago Arteaga | Verificado |
| ERR-11 | 17/11/2026 | Backend | CU-24 | Alta | Restaurar un archivo de respaldo incompleto (sin tablas) vaciaba toda la base y dejaba la plataforma sin administradores ni planes (hallado en revisión independiente) | Admin › Respaldos › Restaurar desde archivo {tables:{}} › RESTAURAR | Rechazar respaldos incompletos, sin admin activo o sin planes sin tocar los datos | Santiago Arteaga | Verificado |
| ERR-12 | 17/11/2026 | Backend | CU-24 | Media | Una restauración que fallaba a mitad respondía 500 y dejaba un archivo de respaldo huérfano | Restaurar un archivo con una fila duplicada | Responder 400 explicando el motivo, sin cambios ni archivos sueltos | Santiago Arteaga | Verificado |
| ERR-13 | 17/11/2026 | Backend / Docker | CU-24 | Baja | La bitácora mostraba la IP del proxy nginx en lugar de la del usuario | Iniciar sesión a través de nginx | Registrar la IP real (X-Forwarded-For de un proxy interno) | Santiago Arteaga | Verificado |
| ERR-14 | 17/11/2026 | Backend | CU-24 | Baja | Restablecer una contraseña desde el admin quedaba como «Otra acción administrativa» | Admin › Usuarios › restablecer contraseña | Acción propia con el usuario afectado | Santiago Arteaga | Verificado |
| ERR-15 | 17/11/2026 | Backend / Frontend | CU-21, CU-24 | Media | El admin no podía asignar un estilo de comunicación a un ejercicio nuevo | Admin › Crear ejercicio en categoría Estilos | Elegir el estilo; el ejercicio aparece en Estilos y requiere Premium | Santiago Arteaga | Verificado |
| ERR-08 | 17/11/2026 | Integración / BD | — | Baja | La suite de pruebas falla con «too many clients» si hay un backend local conectado a la misma base | Levantar backend local y ejecutar mvn test | Pruebas independientes del backend local | Santiago Arteaga | En proceso |
| ERR-09 | 17/11/2026 | Backend | CU-24 | Baja | Un elemento nulo en la lista de límites del plan responde 500 en vez de 400 | PUT /api/admin/plans/FREE con entitlements:[null] | Responder 400 con mensaje de validación | Santiago Arteaga | Verificado |
| ERR-10 | 17/11/2026 | Backend | CU-24 | Baja | Una duración máxima de audio inválida por variable de entorno ya no se avisa al arrancar | Arrancar con AUDIO_MAX_DURATION_SECONDS=0 | Fallar al arrancar con mensaje claro | Santiago Arteaga | Verificado |

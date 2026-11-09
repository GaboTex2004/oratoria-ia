# PostgreSQL y conservación de datos

Actualización: V4 incorpora la pasarela académica de pagos, precio de planes,
estado pendiente y periodos de Subscription, órdenes/transacciones/eventos,
auditoría, reloj por usuario y reservas de consumo. Se reutiliza PlanEntitlement
para features/límites. [Modelo, seguridad y operación](saas/MODO_DEMOSTRACION_PAGOS.md).

PostgreSQL 17, esquema `public`. Puerto Compose local 5435, interno 5432, base
`oratoria_db`, contenedor `oratoria_postgres`; el volumen existente se conserva.
`oratoria_test` y `oratoria_migration_test` son bases separadas para pruebas, no
cuentas ni tablas de demostración de producción. TEST_DB_* no hereda DB_*.

## Migraciones

Flyway, integrado con Spring Boot, es la autoridad del esquema; Hibernate usa
`ddl-auto=validate`. V1 contiene el esquema anterior real obtenido de PostgreSQL,
sin datos ni propietarios. V2 agrega capacidades de entrenamiento local sin borrar
tablas. V3 agrega una cola durable de borrado físico.

Para base vacía se ejecutan V1–V3. Para adoptar una base existente de la versión
anterior: hacer backup, comparar con V1 y habilitar explícitamente
`FLYWAY_BASELINE_ON_MIGRATE=true`. Flyway registra baseline 1 y aplica V2–V3.
El inicializador local activa esa adopción para la copia de desarrollo; no debe
usarse indiscriminadamente con esquemas desconocidos.

Se probó tanto la adopción de la base de pruebas existente como el arranque sobre
`oratoria_migration_test` inicialmente vacía. Nunca ejecutar `docker compose down -v`
como un paso de actualización. No cambiar una migración ya aplicada: crear V4, V5…

## Diccionario resumido

Todos los identificadores de dominio son UUID; fechas son instantes con zona horaria.
El detalle exacto de tipos, restricciones y longitudes está en las migraciones y entidades.

| Tabla | Propósito y campos principales | Integridad |
|---|---|---|
| app_users | email, first_name, last_name, display_name, password_hash, role, enabled, token_version, created_at/updated_at | email único, rol USER/ADMIN; contraseña BCrypt; no se expone hash |
| plans | code FREE/PREMIUM, name, active | code único |
| plan_entitlements | plan_id, feature o usage_limit, enabled, limit_value, unlimited | unicidad por plan/feature y por plan/usage_limit |
| subscriptions | user_id, plan_id, status, starts_at, ends_at | FKs a usuario/plan; consulta de suscripción activa |
| exercises | code, title, description, prompt, training_type, category, difficulty, objective, recommended_duration_seconds, active | code único; bajas lógicas preservan sesiones históricas |
| training_sessions | user_id, exercise_id, training_type, status, fechas, duration_seconds, overall_score, metadatos de audio | usuario obligatorio, ejercicio opcional; archivos fuera de PostgreSQL |
| speech_analyses | session_id, status, transcript_text, segments_json, words_json, filler_counts_json, métricas temporales, acoustic_metrics_json, intentos/errores | una fila por sesión; FK/unique |
| speech_evaluations | session_id, estado, versión de rúbrica, proveedor/modelo, evidencia, score, criterios/fortalezas/recomendaciones JSON, intentos/errores | una evaluación por sesión; FK/unique |
| simulation_sessions | user_id, scenario_id, modalidad, dificultad, estado, turnos, final_feedback_json, score y fechas | agregado independiente de prácticas |
| simulation_turns | simulation_id, sequence_number, speaker, estado, texto, idempotency_key, metadatos de audio | unique(simulation_id, sequence_number); unique(simulation_id, idempotency_key) |
| body_analyses | session_id, result_json, created_at | una fila por sesión; no conserva fotogramas |
| media_deletion_jobs | storage_path, created_at | cola interna durable; no endpoint público |
| flyway_schema_history | versiones/checksums/resultados de migración | administrada por Flyway |

Índices V2: usuario/fecha en prácticas y simulaciones, usuario/estado en suscripciones.
Justificación: historial y contabilidad mensual por usuario. No se añaden índices
sin una consulta que los utilice. No se afirma que el volumen universitario sea una
prueba de carga de producción.

## Archivos y privacidad

Audio: `AUDIO_STORAGE_DIR`, por defecto `backend/data/audio/<userId>/<sessionOrTurnId>.<ext>`.
Las rutas se normalizan y deben permanecer dentro del directorio. Se sirve solo a
través de endpoints autenticados, con comprobación de propietario y no-store.
No hay directorio público de grabaciones. Los modelos están en `ai-service/models/`.

Eliminar datos de práctica limpia audio, transcripción, métricas y evaluaciones,
incluida pose; eliminar datos conversacionales limpia texto, feedback y audios.
Mientras hay procesamiento activo se rechaza la eliminación. La fila mínima de
sesión/turno permanece para la cuota y trazabilidad; no hay reembolso de cuota por borrar.

La eliminación física se encola en la misma transacción que la limpieza de referencias.
Un proceso cada 30 s elimina archivos; si falla el sistema de archivos, la cola
permanece para reintentar tras un reinicio. Desde que se confirma el borrado, la API
no permite descargar el audio. Copias manuales de seguridad tienen conservación
independiente y deben gestionarse por el responsable. No se programó borrado masivo
automático ni se destruyeron datos de compañeros.

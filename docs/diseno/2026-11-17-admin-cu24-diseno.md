# CU-24 Administrar usuarios, ejercicios, planes y parámetros del sistema — diseño

Fecha: 2026-11-17 · Autor: Santiago Arteaga · Aprobado por el usuario en conversación.

## Intención

Completar el "sector del administrador" que pide el Documento Grupal SW1 (alcance
"Administración del sistema" + "Reportes del sistema" e HU4-06) sobre lo que ya existe
en `main` (0161432). Hoy el admin gestiona usuarios (habilitar, plan, contraseña) y
ejercicios. Falta todo lo demás que lista el documento.

## Objetivos

1. **Resumen / estadísticas:** usuarios (total, activos), prácticas y simulaciones del mes,
   ejercicios más usados, dificultades frecuentes (criterios de evaluación con nota media más baja).
2. **Categorías de entrenamiento administrables:** tabla `exercise_categories` (código, nombre,
   descripción, activa), sembrada con las 9 actuales. La validación de ejercicios pasa a usar la tabla.
3. **Planes:** editar nombre, precio, activo, límites mensuales (`plan_entitlements` de tipo
   `UsageLimit`) y funciones habilitadas (`Feature`) de FREE y PREMIUM.
4. **Sistema:** estado en vivo del servicio de IA (`GET {ai}/health`) y de la base de datos;
   parámetros generales editables guardados en tabla `system_settings` (clave/valor):
   - `registration.enabled` (bool): si es `false`, `POST /api/auth/register` responde 403.
   - `audio.max-duration-seconds` (int 15..600): reemplaza en caliente el valor de propiedades.
   - `announcement.message` (texto ≤ 300, vacío = sin aviso): visible para usuarios autenticados
     vía `GET /api/system/announcement`.

## No objetivos

- No se tocan los flujos de usuarios/ejercicios/pagos simulados que ya funcionan, salvo que la
  categoría pase de lista fija a tabla.
- No hay roles nuevos (solo `ADMIN`).
- Actualización (17-nov, a pedido del equipo): se agregaron después las pestañas **Reportes** (ejercicios
  completados y dificultades frecuentes, con PDF), **Bitácora** (acciones de autenticación y administrativas) y
  **Respaldos** (generar, descargar, eliminar y restaurar la base; la restauración cierra todas las sesiones y
  no incluye los audios).
- No se crean planes nuevos: solo se editan FREE y PREMIUM (`PlanCode` sigue siendo enum).

## Restricciones

- Spring Boot 4 / Java 21, Flyway (siguiente migración `V5__admin_system.sql`), `ddl-auto=validate`.
- Todo bajo `/api/admin/**` exige rol ADMIN (ya lo hace `SecurityConfig`).
- Frontend React + TS, reutilizar `WorkspaceLayout`, `useResource`, `apiRequest` y las clases CSS existentes.

## Enfoques considerados

- **A (elegido): ampliar el panel `/admin` existente con pestañas** (Resumen · Usuarios ·
  Ejercicios y categorías · Planes · Sistema) y extender `AdminManagementController/Service`
  con servicios nuevos por área. Reusa todo y no rompe rutas.
- B: páginas separadas por área (`/admin/plans`, `/admin/system`…). Más rutas y navegación sin ganancia.
- C: parámetros en `application.properties` editados a mano. No cumple "gestión de parámetros" desde la plataforma.

## Decisiones de diseño

- Los 3 parámetros del sistema arriba son una propuesta inicial; se pueden cambiar.
- Estadísticas se calculan con consultas agregadas en el backend, sin tabla nueva.
- Gráficas con barras CSS simples (sin librería nueva).
- Pruebas de integración backend (`AdminSystemIntegrationTests`) por cada endpoint nuevo,
  incluido rechazo a no-admin.

# Arquitectura

## Visión general

Oratoria IA comienza como un monolito modular con un servicio separado para IA:

```text
React :5173 -> REST -> Spring Boot :8081 -> PostgreSQL :5434
                            |
                            +-> REST futuro -> FastAPI :8000
```

React presenta la experiencia de usuario y organiza la UI por feature. Spring Boot
es la autoridad del negocio, persistencia, usuarios, suscripciones, sesiones y acceso
a funcionalidades. PostgreSQL conserva el estado transaccional. FastAPI procesará
audio/video/texto en trabajos posteriores; no será la fuente de verdad del SaaS.

## Backend modular

Cada dominio vive bajo `com.oratoria.backend.<dominio>` y puede contener sus propios
`controller`, `service`, `repository`, `entity`, `dto` y `mapper` cuando los necesite.
No se crean esas capas hasta que exista un caso de uso real.

- `auth`: credenciales, JWT, recuperación y autorización futura.
- `user`: perfil, preferencias y objetivos.
- `subscription`: planes, suscripciones, features y límites centralizados.
- `training`: catálogo de modalidades de entrenamiento.
- `session`: ciclo de vida de cada práctica.
- `analysis`: resultados y eventos temporales producidos por IA.
- `progress`: historial, evolución y comparación.
- `exercise`: catálogo y asignación de ejercicios.
- `simulation`: simulaciones futuras asistidas por IA.
- `config`: configuración técnica de Spring.
- `shared`: contratos realmente transversales; no debe contener lógica de dominios.
- `infrastructure`: endpoints técnicos, como `/api/test`.

Los controladores reciben/devuelven DTOs, delegan casos de uso a servicios y nunca
exponen entidades JPA. Los repositorios solo persisten. Se prefieren relaciones JPA
unidireccionales para reducir acoplamiento y problemas de serialización.

## SaaS y freemium

`Plan` identifica productos (`FREE`, `PREMIUM`, y futuros `PRO` o `TEAM`).
`Subscription` vincula un usuario con un plan sin conocer Stripe o Mercado Pago.
`PlanEntitlement` configura dos tipos de derecho:

- `Feature`: capacidad booleana, consultada con `hasAccess(userId, feature)`.
- `UsageLimit`: cuota numérica o ilimitada, consultada con `limitFor(userId, limit)`.

Toda decisión de plan pasa por `FeatureAccessService`. Un módulo consumidor pregunta
por una capacidad o límite y no evalúa nombres de planes. El consumo mensual todavía
no se contabiliza; cuando se implemente, vivirá detrás de este mismo límite de dominio.

El plan `FREE` se inicializa de forma idempotente al arrancar. El registro público
siempre crea un usuario con rol `USER` y una única suscripción `FREE` dentro de la
misma transacción. Rol (`USER`/`ADMIN`) y plan son conceptos independientes.

## Autenticación y autorización

Spring Security mantiene una API sin sesión de servidor. Las contraseñas se almacenan
con BCrypt y el login entrega un JWT firmado con `JWT_SECRET`. El filtro valida el
token y vuelve a obtener rol y estado desde PostgreSQL. Registro, login y `/api/test`
son públicos; `/api/users/**` requiere autenticación y `/api/admin/**` requiere rol
`ADMIN`. El frontend conserva el token y reconstruye el usuario con `/api/users/me`.

El administrador inicial sólo puede crearse mediante variables `ADMIN_*` durante el
arranque. No existe registro público de administradores ni se acepta un rol del cliente.

## Frontend por features

`src/app` compone la aplicación. `app/router/AppRouter.tsx` declara las rutas públicas
`/login` y `/register`, el dashboard `/home` y placeholders para módulos futuros.
`src/features/<feature>` agrupa páginas, componentes, datos, servicios y tipos propios;
actualmente `auth` contiene formularios, cliente HTTP, contexto y rutas protegidas, mientras `dashboard` contiene
el layout y sus datos mock centralizados. `src/shared` conserva componentes y servicios
reutilizados por varias features. La URL HTTP se resuelve en `shared/config/env.ts` y
`shared/services/api.ts` mantiene el chequeo de infraestructura `GET /api/test`; una API
exclusiva de una feature debe permanecer dentro de ella.

## AI Service

FastAPI tendrá rutas y schemas del contrato HTTP y servicios separados para `voice`,
`pose`, `face`, `speech` y `evaluation`. Los cargadores de modelos vivirán en `models`
y la configuración técnica en `core`. Spring invocará el servicio por REST y guardará
el estado del trabajo y resultados relevantes. Actualmente solo existe `/health`.

## Flujo futuro de análisis

1. React crea una sesión mediante Spring Boot.
2. Spring valida suscripción, feature y cuota.
3. El medio se carga en almacenamiento de objetos (no en PostgreSQL).
4. Spring solicita el análisis a FastAPI con referencias seguras al medio.
5. FastAPI devuelve o notifica resultados estructurados.
6. Spring persiste métricas/eventos y React consulta el progreso.

## Agregar un módulo

1. Definir su responsabilidad y dueño de datos; evitar duplicar conceptos existentes.
2. Crear el paquete raíz del dominio y solo las capas requeridas por el primer caso.
3. Mantener dependencias hacia contratos públicos, no hacia internals de otro módulo.
4. Crear DTOs en el límite HTTP y pruebas del servicio/caso de uso.
5. Documentar nuevas variables en `.env.example` y decisiones relevantes aquí.

## Convenciones

- Paquetes y rutas en minúsculas; clases con nombres de dominio explícitos.
- Fechas persistidas como `Instant`; IDs como UUID.
- Secretos únicamente mediante entorno, nunca en Git.
- Endpoints bajo `/api`; health checks técnicos separados del dominio.
- Cambios de esquema futuros mediante migraciones versionadas.

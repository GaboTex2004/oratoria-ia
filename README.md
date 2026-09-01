# Oratoria IA

Plataforma web de entrenamiento de comunicación y oratoria mediante inteligencia
artificial. El producto busca analizar voz y expresión corporal, ofrecer
entrenamientos y simulaciones, y presentar retroalimentación útil sobre el desempeño.

## Estado del proyecto

### Implementado actualmente

- PostgreSQL 17 ejecutado mediante Docker Compose y persistencia en volumen.
- API Spring Boot conectada a PostgreSQL.
- Registro y login de usuarios con contraseñas BCrypt y JWT.
- Roles `USER` y `ADMIN`, rutas protegidas y administrador inicial configurable.
- Planes `FREE` y `PREMIUM` modelados; alta automática con plan `FREE`.
- Frontend React con autenticación, dashboard y rutas protegidas.
- Servicio base FastAPI con endpoints `/` y `/health`.

### Planificado o en desarrollo

- Análisis avanzado de voz, postura, rostro y discurso.
- Modelos de IA y generación de retroalimentación automática.
- Entrenamientos, simulaciones y métricas completas de progreso.
- Integración efectiva entre Spring Boot y los análisis de FastAPI.
- Funcionalidades comerciales completas del plan `PREMIUM`.

| Componente | Estado actual |
| --- | --- |
| PostgreSQL y Docker Compose | Implementado y comprobable localmente |
| Spring Boot y JPA | Implementado |
| React y Vite | Implementado |
| Registro y login `USER` | Implementado |
| BCrypt y JWT | Implementado |
| Plan `FREE` | Implementado e inicializado al arrancar |
| Plan `PREMIUM` | Modelado; prestaciones comerciales pendientes |
| Administrador inicial | Implementado; requiere variables reales al arrancar |
| FastAPI base | Implementado, aún sin modelos de IA |
| Análisis avanzado de IA | En desarrollo |

## Stack tecnológico

- Frontend: React 19, TypeScript 6 y Vite 8.
- Backend: Spring Boot 4, Java 21 y Maven.
- Base de datos: PostgreSQL 17 en Docker.
- Servicio de IA: Python, FastAPI y Uvicorn.

Las librerías y modelos especializados para análisis de voz, postura, rostro y texto
son trabajo futuro; todavía no existe una canalización funcional de IA.

## Arquitectura

```text
React :5173
  |
  | HTTP / REST
  v
Spring Boot :8081
  |
  +---- PostgreSQL :5434
  |
  +---- FastAPI :8000
           |
           +---- servicios y modelos de IA futuros
```

- React ofrece la interfaz, mantiene la sesión local y consume la API principal.
- Spring Boot concentra autenticación, usuarios, roles, planes, suscripciones y la
  futura lógica de sesiones y entrenamiento.
- PostgreSQL es la fuente de verdad para los datos transaccionales.
- FastAPI contiene la base del procesamiento relacionado con IA. Todavía no está
  conectado al backend ni contiene modelos entrenados.

Consulta [la arquitectura](docs/ARCHITECTURE.md) para conocer los módulos del sistema.

## Requisitos

- Git.
- Java 21 (`java -version`).
- Node.js `20.19+` o `22.12+` y npm, según el requisito de Vite 8
  (`node --version`, `npm --version`).
- Docker Desktop con Docker Compose (`docker compose version`).
- Python 3.10 o posterior recomendado (`python --version`).
- PowerShell para seguir literalmente los ejemplos de Windows.

El repositorio incluye `backend/mvnw` y `backend/mvnw.cmd`; no es necesario instalar
Maven globalmente.

## Instalación

```powershell
git clone https://github.com/GaboTex2004/oratoria-ia.git
cd oratoria-ia
```

Cada componente administra sus dependencias. Los pasos siguientes parten de la raíz.

## PostgreSQL con Docker

La configuración real de `docker-compose.yml` es:

- servicio: `postgres`;
- contenedor: `oratoria_postgres`;
- imagen: PostgreSQL 17;
- host: `localhost:5434`;
- puerto interno: `5432`;
- base predeterminada: `oratoria_db`;
- volumen: `oratoria_postgres_data`.

Para desarrollo local puede copiarse la plantilla raíz. Sus valores predeterminados
`postgres/postgres` son exclusivamente credenciales locales, no de producción.

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

Docker Compose sí carga automáticamente el `.env` de la raíz. No uses
`docker compose down -v` como procedimiento habitual: elimina volumen y datos.

## Variables de entorno

### Docker Compose

| Variable | Propósito | Default local |
| --- | --- | --- |
| `POSTGRES_DB` | Base inicial | `oratoria_db` |
| `POSTGRES_USER` | Usuario de PostgreSQL | `postgres` |
| `POSTGRES_PASSWORD` | Contraseña local | `postgres` |
| `POSTGRES_PORT` | Puerto publicado | `5434` |

### Backend Spring Boot

| Variable | Propósito | Default |
| --- | --- | --- |
| `SERVER_PORT` | Puerto HTTP | `8081` |
| `DB_URL` | URL JDBC | `jdbc:postgresql://localhost:5434/oratoria_db` |
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña PostgreSQL | `postgres` |
| `CORS_ALLOWED_ORIGIN` | Origen de React | `http://localhost:5173` |
| `AI_SERVICE_URL` | URL base de FastAPI | `http://localhost:8000` |
| `JWT_SECRET` | Clave de firma JWT | Sin default; obligatoria |
| `JWT_EXPIRATION_MS` | Duración del JWT | `86400000` |
| `ADMIN_EMAIL` | Email del administrador inicial | Vacío; opcional |
| `ADMIN_PASSWORD` | Contraseña inicial | Vacío; opcional |
| `ADMIN_FIRST_NAME` | Nombre inicial | `Administrador` |
| `ADMIN_LAST_NAME` | Apellido inicial | `Oratoria IA` |

> **Importante:** Spring Boot no carga `backend/.env` automáticamente.
> `backend/.env.example` sólo es plantilla/documentación. Crear `backend/.env` no
> configura Java: no hay dotenv ni `spring.config.import` para ese archivo.

### Configurar el backend en Windows/PowerShell

Usa placeholders y ejecuta Spring desde la misma terminal:

```powershell
$env:SERVER_PORT="8081"
$env:DB_URL="jdbc:postgresql://localhost:5434/oratoria_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
$env:CORS_ALLOWED_ORIGIN="http://localhost:5173"
$env:AI_SERVICE_URL="http://localhost:8000"
$env:JWT_SECRET="<TU_JWT_SECRET>"
$env:JWT_EXPIRATION_MS="86400000"
$env:ADMIN_EMAIL="<TU_EMAIL_ADMIN>"
$env:ADMIN_PASSWORD="<TU_PASSWORD_ADMIN>"
$env:ADMIN_FIRST_NAME="<TU_NOMBRE>"
$env:ADMIN_LAST_NAME="<TU_APELLIDO>"
```

Estas variables sólo existen en esa sesión de PowerShell. Una terminal distinta no
las hereda. Nunca escribas credenciales reales dentro de un archivo versionado.

### Generar `JWT_SECRET` en PowerShell

Sin OpenSSL, este ejemplo genera 32 bytes criptográficamente aleatorios:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Conserva el secreto de forma segura entre arranques. No lo subas a GitHub ni generes
uno diferente en cada ejecución: cambiarlo invalida los JWT emitidos anteriormente.

### Frontend

`frontend/.env.example` declara:

```dotenv
VITE_API_URL=http://localhost:8081/api
```

Vite sí carga `frontend/.env`. Las variables `VITE_*` quedan visibles en el navegador:
nunca coloques allí `JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD` u otros secretos.

### Servicio FastAPI

| Variable | Default | Propósito |
| --- | --- | --- |
| `AI_SERVICE_HOST` | `0.0.0.0` | Interfaz de escucha |
| `AI_SERVICE_PORT` | `8000` | Puerto HTTP |
| `SPRING_API_URL` | `http://localhost:8081/api` | URL futura de Spring |
| `APP_ENV` | `development` | Entorno y modo debug |

FastAPI tampoco carga `ai-service/.env` automáticamente; `app/core/config.py` obtiene
estas variables directamente del entorno del proceso.

## Administrador inicial

`AdminInitializer` se ejecuta al arrancar Spring, después de garantizar el plan `FREE`:

1. Lee las cuatro variables `ADMIN_*`.
2. Si email y contraseña están vacíos, no crea administrador.
3. Si sólo uno está configurado o la contraseña tiene menos de 8 caracteres, detiene
   el arranque con un error de configuración.
4. Normaliza el email con `trim` y minúsculas.
5. Si el email ya existe, no duplica ni modifica usuario o contraseña.
6. Si no existe, crea un usuario habilitado, rol `ADMIN`, contraseña BCrypt y una
   suscripción activa al plan `FREE`.

No existe registro público de administradores. `POST /api/auth/register` siempre crea
un `USER`. Nunca insertes manualmente un `password_hash` en PostgreSQL.

## Roles y planes

- Rol `USER` o `ADMIN`: controla permisos y rutas.
- Plan `FREE` o `PREMIUM`: controla prestaciones y límites del SaaS.

Son conceptos independientes. Son posibles `USER + FREE` y `USER + PREMIUM`; un
usuario `PREMIUM` no se convierte en administrador y `ADMIN` no depende de `PREMIUM`.
El administrador inicial recibe actualmente el plan `FREE`.

## Ejecutar los componentes

### Backend Spring Boot

Después de definir las variables en esa misma terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

- API: <http://localhost:8081>
- Prueba pública: `GET http://localhost:8081/api/test`

### Frontend React

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

- Aplicación: <http://localhost:5173>

### Servicio FastAPI

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Servicio: <http://localhost:8000>
- Estado: `GET http://localhost:8000/health`
- OpenAPI: <http://localhost:8000/docs>

El servicio actual es un esqueleto; ejecutarlo no habilita análisis de IA.

## Inicio rápido

Abre cuatro terminales en la raíz del repositorio.

### Terminal 1 — PostgreSQL

```powershell
docker compose up -d
docker compose ps
```

### Terminal 2 — Spring Boot

```powershell
# Define antes DB_*, JWT_SECRET y, si corresponde, ADMIN_*.
cd backend
.\mvnw.cmd spring-boot:run
```

### Terminal 3 — FastAPI

```powershell
cd ai-service
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Terminal 4 — React

```powershell
cd frontend
npm run dev
```

## Flujo de autenticación

```text
Registro:
React -> POST /api/auth/register -> Spring -> BCrypt -> PostgreSQL
                                                |
                                                +-> USER + FREE -> JWT

Login:
React -> POST /api/auth/login -> Spring -> PostgreSQL -> BCrypt -> JWT
```

Después de autenticar:

- `USER` navega a `/home`.
- `ADMIN` navega a `/admin`.
- `GET /api/users/me` reconstruye la sesión.
- `GET /api/admin/status` comprueba autorización administrativa.

La API es stateless: el logout del frontend elimina el token local.

## Solución de problemas

### `ERR_CONNECTION_REFUSED` en `localhost:8081`

Spring Boot no está ejecutándose o falló al arrancar. Revisa su terminal y comprueba
que Tomcat haya iniciado en `8081`.

### Docker o PostgreSQL no conectan

Comprueba Docker Desktop y ejecuta:

```powershell
docker compose ps
docker compose logs postgres
```

El backend local usa `localhost:5434`, no el servicio `postgres`, que sólo es resoluble
desde contenedores de la misma red.

### El administrador recibe `401 Unauthorized`

Comprueba que `ADMIN_EMAIL` y `ADMIN_PASSWORD` estuvieran en la misma terminal que
inició Spring. `backend/.env` por sí solo no se carga. Si faltan, el inicializador se
omite silenciosamente y no crea la cuenta.

Si el email ya estaba registrado como `USER`, el inicializador no lo promueve, no
cambia su contraseña y tampoco duplica el email.

### Falta `JWT_SECRET`

Es obligatorio y debe tener al menos 32 caracteres. Configúralo antes de iniciar Maven;
no agregues un valor real a `application.properties`.

### Puerto ocupado

Identifica primero el proceso; no lo cierres automáticamente:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8081
Get-Process -Id <PID>
```

También puedes cambiar temporalmente `SERVER_PORT`, sincronizando `VITE_API_URL`.

### Hibernate no puede determinar el dialecto

Normalmente significa que no pudo abrir la conexión JDBC, no que debas forzar
`hibernate.dialect`. Comprueba Docker, `DB_URL`, credenciales, puerto y base.

## Seguridad

- Nunca subas `.env`, contraseñas, hashes completos, JWT ni `JWT_SECRET`.
- Los `.env.example` con placeholders sí se versionan.
- Nunca expongas secretos mediante variables `VITE_*`.
- Las contraseñas se almacenan mediante BCrypt.
- El registro público nunca permite seleccionar rol ni crear administradores.
- Sustituye los defaults locales en cualquier entorno no local.

El `.gitignore` excluye `.env`, `.env.*`, `backend/.env`, `frontend/.env` y
`ai-service/.env`, con excepciones explícitas para los `.env.example`.

## Flujo de trabajo con Git

No desarrolles directamente sobre `main`. Crea una rama pequeña por cambio:

```powershell
git checkout main
git pull origin main
git checkout -b feature/voice-analysis
```

Ejecuta las comprobaciones del componente, crea commits claros y abre un Pull Request.
Evita mezclar una funcionalidad con refactors no relacionados. Consulta también
[el flujo del equipo](docs/TEAM_WORKFLOW.md).

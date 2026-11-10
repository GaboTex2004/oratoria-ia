# Docker integral en Windows

Esta modalidad ejecuta React/Nginx, Spring Boot, FastAPI, PostgreSQL y Ollama sin
instalar Java, Node, Python, PostgreSQL ni Ollama en el host. Solo requiere Docker
Desktop con contenedores Linux, Docker Compose v2 y PowerShell.

## Recursos y aislamiento

- Proyecto Compose fijo: `oratoriaai-local`.
- URL pública predeterminada: `http://127.0.0.1:15173`.
- Solo Nginx publica un puerto, ligado a loopback.
- PostgreSQL, Spring, FastAPI y Ollama solo están en la red interna de Compose.
- No se usa `container_name`; Docker agrega el prefijo del proyecto.
- La base se llama `oratoriaai_local`, no `oratoria_db`.
- Los volúmenes nuevos tienen prefijo `oratoriaai-local_` y nunca reutilizan
  `oratoria-ia_oratoria_postgres_data`.

Recomendación inicial para CPU: 16 GB de RAM y al menos 15 GB libres. La descarga
inicial necesita internet. Los arranques siguientes reutilizan capas e inferencia.

## Primer arranque

Desde la raíz del repositorio:

```powershell
.\scripts\Up-Docker.ps1
```

El script comprueba Docker, conserva las claves existentes del `.env`, agrega solo
las faltantes, genera secretos criptográficos y ejecuta `docker compose up` con
build y espera de salud. Los valores secretos no se imprimen.

Secuencia de arranque:

1. PostgreSQL 17 crea una base vacía en un volumen propio.
2. Ollama arranca y `ollama-model-init` descarga `qwen2.5:1.5b` si falta.
3. `ai-model-init` descarga y verifica `Systran/faster-whisper-base` y
   `pose_landmarker_lite.task` si faltan.
4. FastAPI arranca con un worker y perfil CPU.
5. Spring aplica Flyway V1, V2, V3 y V4 con baseline desactivado y valida Hibernate.
6. Nginx sirve la SPA y envía `/api` al backend por DNS interno.

No se deben copiar secretos a variables `VITE_*`. El navegador solo conoce `/api`.

## Uso cotidiano

```powershell
docker compose up -d --build
docker compose ps
docker compose logs --tail 200 backend ai-service ollama
```

Detener sin perder datos:

```powershell
docker compose down
```

`down` conserva los cuatro volúmenes. No usar `docker compose down -v`, porque
eliminaría base, modelos y medios privados.

Para cambiar el puerto, editar o agregar en el `.env` local:

```dotenv
FRONTEND_PORT=15174
```

Luego recrear solo el frontend con `docker compose up -d frontend`.

## Servicios y salud

```powershell
docker compose ps
Invoke-WebRequest http://127.0.0.1:15173/healthz -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:15173/api/test -UseBasicParsing
docker compose exec -T ollama ollama show qwen2.5:1.5b
docker compose exec -T ai-service python scripts/download_models.py --whisper base --pose --verify-only
```

El endpoint `/health` de FastAPI certifica el proceso, no una inferencia. La prueba
real usa audio sintético identificado y se describe más abajo.

## Pruebas

Suites Java, Python, frontend y Playwright sin modelos:

```powershell
.\scripts\Test-Docker.ps1
```

Recorrido completo con Whisper, Qwen y dispositivos sintéticos:

```powershell
.\scripts\New-SyntheticAudio.ps1
.\scripts\Test-Docker.ps1 -Models
```

Los tests Java usan `postgres-test`, una base efímera en `tmpfs`, y no reciben la
URL de la base de desarrollo ni de instalaciones anteriores. Los pagos son siempre
académicos y solo se habilitan con perfil `dev` y `BILLING_DEMO_ENABLED=true`.

## Persistencia

Los volúmenes son:

- `oratoriaai-local_postgres_data`: base nueva.
- `oratoriaai-local_ollama_models`: Qwen.
- `oratoriaai-local_ai_models`: Whisper y MediaPipe.
- `oratoriaai-local_private_media`: grabaciones privadas.

Verificación recomendada: crear un usuario de prueba, registrar su correo, ejecutar
`docker compose down`, volver a ejecutar `docker compose up -d --wait` y comprobar
que puede iniciar sesión y que `ollama show` y `--verify-only` no descargan de nuevo.

## Respaldo y restauración

Crear un respaldo de la base nueva sin exponer la contraseña:

```powershell
New-Item -ItemType Directory -Force .tools\backups | Out-Null
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file=/tmp/oratoriaai.backup'
docker compose cp postgres:/tmp/oratoriaai.backup .tools/backups/oratoriaai.backup
docker compose exec -T postgres rm -f /tmp/oratoriaai.backup
```

Restaurar reemplaza el contenido de la base nueva. Antes de hacerlo, detener el
backend, crear otro respaldo y confirmar que el destino pertenece a
`oratoriaai-local`. Copiar el archivo al contenedor y usar `pg_restore` contra
`$POSTGRES_DB`. No restaurar automáticamente sobre `oratoria_db` ni sobre el
contenedor antiguo.

La migración opcional desde una base anterior requiere autorización expresa, un
backup externo verificado y comparación de esquema/checksums Flyway. No forma parte
del arranque automático.

## Perfil NVIDIA opcional

El perfil básico usa CPU, Whisper `int8`, un worker Uvicorn y `LLM_NUM_GPU=0`. Con
NVIDIA Container Toolkit ya configurado se puede usar:

```powershell
$env:LLM_NUM_GPU='99'
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
```

Esto asigna GPU a Ollama; Whisper permanece en CPU salvo que se configure y pruebe
explícitamente una combinación CUDA compatible.

## Recuperación

- Si un inicializador falla, revisar `docker compose logs ai-model-init
  ollama-model-init` y repetir `docker compose up -d`; las descargas verificadas se
  reutilizan.
- Si Spring falla, revisar `docker compose logs backend` y la tabla
  `flyway_schema_history`. No editar migraciones V1–V4 ni activar baseline.
- Si el puerto está ocupado, cambiar `FRONTEND_PORT` sin publicar PostgreSQL.
- Si Docker queda sin espacio, liberar espacio del disco de Docker desde Docker
  Desktop. No ejecutar `docker system prune`, `docker volume prune` ni `down -v`.
- Si el disco virtual está en C: y no hay margen suficiente para las imágenes y
  modelos, usar **Docker Desktop > Settings > Resources > Advanced > Disk image
  location**, seleccionar una carpeta de D: y aplicar el cambio. Docker Desktop
  se reinicia durante el traslado, por lo que primero se debe comprobar el respaldo
  y coordinar la interrupción de los demás contenedores. No mover, sustituir ni
  enlazar manualmente el archivo VHDX.
- La ejecución manual sigue disponible con los scripts `Start-*`; usa puertos del
  host y sus `.env` por componente. Docker usa DNS interno y volúmenes aislados.

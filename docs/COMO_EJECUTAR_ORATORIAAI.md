# Cómo ejecutar OratoriaAI con Docker

> **Proyecto:** OratoriaAI  
> **Rama de desarrollo:** `Antony/dockerizacion`  
> **Entorno probado:** Windows, PowerShell, Docker Desktop con WSL 2  
> **Estado:** el frontend, backend, FastAPI, PostgreSQL y Ollama llegaron a ejecutarse en Docker. El flujo de cámara/análisis corporal queda pendiente de corrección y validación.

## 1. Arquitectura

```text
Navegador (http://127.0.0.1:15173)
            |
      Frontend React/Vite
       servido por Nginx
            | /api
            v
     Backend Spring Boot (Java 21)
            |                 \
            |                  \ HTTP con clave interna
            v                   v
      PostgreSQL 17         FastAPI (Python)
                                 |       \
                                 |        \ Whisper Base (voz)
                                 |         \ MediaPipe Pose (cámara/postura)
                                 v
                          Ollama + Qwen 2.5 1.5B
```

Los servicios internos se comunican a través de las redes de Docker Compose. El puerto publicado para utilizar la aplicación en esta configuración es **15173**, accesible desde el propio equipo.

## 2. Requisitos

- Windows con **Docker Desktop**, WSL 2 y Docker Compose operativos.
- PowerShell para usar los scripts `.ps1`.
- Espacio libre suficiente para imágenes, modelos de IA y volúmenes persistentes; la primera instalación descarga varios gigabytes.
- Internet para el primer arranque y la descarga de modelos. Después, las descargas persistidas deberían reutilizarse.

**No hace falta instalar Java, Python, Node.js, PostgreSQL ni Ollama en Windows para ejecutar la versión Docker.** Estos componentes se ejecutan en contenedores.

> **Importante:** Docker Desktop debe indicar que su motor está funcionando. Compruébalo con `docker version`: tienen que aparecer las secciones `Client` y `Server`. Si solo aparece `Client`, primero hay que resolver Docker Desktop.

## 3. Primer arranque (recomendado)

Abrir PowerShell en la carpeta raíz del repositorio:

```powershell
cd D:\Universidad\Sw1\oratoria-ia
```

En otro equipo, sustituir esa ruta por el directorio donde se haya clonado el repositorio.

Comprobar Docker:

```powershell
docker version
docker compose version
```

Ejecutar el script de inicialización:

```powershell
.\scripts\Up-Docker.ps1
```

El script prepara el entorno local, construye o inicia los servicios necesarios, comprueba dependencias y utiliza los modelos persistentes. En una instalación nueva, la preparación puede requerir descargar las imágenes y los modelos de IA.

**Dirección para abrir el sistema:**

```text
http://127.0.0.1:15173/
```

Los archivos de configuración local `.env` contienen credenciales y **no deben subirse a Git**. Revisar las variables de ejemplo y seguir las instrucciones del script si solicita configuración.

## 4. Arranque habitual

En los siguientes usos, abrir Docker Desktop y ejecutar desde la raíz del repositorio:

```powershell
cd D:\Universidad\Sw1\oratoria-ia
docker compose ps --all
```

Si los contenedores principales ya existen y solo están detenidos:

```powershell
docker compose start postgres ollama ai-service backend frontend
```

Si se necesita aplicar la configuración actual del proyecto sin reconstruir imágenes:

```powershell
docker compose up -d --no-build
```

Para reconstruir las imágenes cuando haya cambios en el código o Dockerfiles:

```powershell
docker compose up -d --build
```

**Precaución:** una compilación puede consumir bastante almacenamiento. No la ejecutes si el disco de Docker o la unidad del sistema están casi llenos.

## 5. Consultar estado y registros

```powershell
# Ver servicios actuales
docker compose ps

# Ver también contenedores terminados
docker compose ps --all

# Registros recientes de cada servicio
docker compose logs --tail 80 frontend
docker compose logs --tail 80 backend
docker compose logs --tail 80 ai-service
docker compose logs --tail 80 ollama
docker compose logs --tail 80 postgres
```

Comprobar que el frontend responde:

```powershell
curl.exe -I --max-time 10 http://127.0.0.1:15173/
```

Se espera una respuesta HTTP `200 OK`. La salud del contenedor no reemplaza esta comprobación: Nginx puede estar `healthy` y aun así devolver un error al cargar la página.

Comprobar los modelos instalados en Ollama:

```powershell
docker compose exec -T ollama ollama list
```

## 6. Detener y volver a iniciar

Detener los contenedores de este proyecto **sin eliminar sus volúmenes**:

```powershell
docker compose stop
```

Reiniciar los contenedores existentes:

```powershell
docker compose start
```

Si se necesita quitar los contenedores y la red Compose, conservando los volúmenes persistentes:

```powershell
docker compose down
```

**Nunca ejecutar `docker compose down -v` si se quieren conservar bases de datos, grabaciones y modelos.** Tampoco utilizar limpiezas globales de Docker sin revisar su efecto sobre otros proyectos de la computadora.

## 7. Ejecutar pruebas

Pruebas integrales definidas en el proyecto:

```powershell
.\scripts\Test-Docker.ps1
```

Pruebas adicionales con los modelos reales y recorridos de extremo a extremo (más pesadas):

```powershell
.\scripts\Test-Docker.ps1 -Models
```

La segunda modalidad puede usar Playwright, audio sintético, Whisper y Qwen; requiere recursos adicionales y no debe ejecutarse con poco espacio libre.

En la validación de la rama se registraron **69/69 pruebas Java aprobadas dentro de Docker**, además de Flyway V1–V4. **La validación integral de Python, Playwright y modelos quedó interrumpida y no debe presentarse como aprobada hasta volver a ejecutarse.**

## 8. Modelos de IA utilizados

| Componente | Tecnología | Uso |
|---|---|---|
| Respuestas y evaluaciones de lenguaje | **Ollama + Qwen 2.5 1.5B** | Generar respuestas y apoyar simulaciones/evaluaciones |
| Reconocimiento de voz | **faster-whisper Base** | Transcribir grabaciones de voz |
| Análisis corporal | **MediaPipe Pose Landmarker Lite** | Obtener puntos de referencia de postura a partir de la cámara/imágenes |

Los modelos se ejecutan localmente en la infraestructura Docker, sin requerir una API comercial para estas funciones.

## 9. Estado conocido y pendiente

- **Operativo en la revisión manual:** interfaz, navegación y servicios principales de la aplicación, según la prueba local del equipo.
- **Pendiente:** corregir/verificar el apartado de **cámara y análisis corporal**. MediaPipe es la tecnología prevista para analizar postura, pero todavía no está comprobado si el fallo proviene de permisos del navegador, captura de video, frontend, integración con FastAPI o procesamiento del modelo.
- **Pendiente:** terminar Playwright y las pruebas integrales con Whisper y Qwen.

Para investigar la cámara, conceder permisos al navegador, utilizar la URL local `127.0.0.1`, abrir las herramientas de desarrollo con `F12` y revisar los registros de `frontend` y `ai-service`. No declarar el módulo de cámara como funcional hasta comprobarlo de principio a fin.

## 10. Problemas frecuentes

**`docker version` solo muestra Client, error 500 o el motor no inicia:** Docker Desktop/WSL no está funcionando correctamente. No reconstruir ni limpiar volúmenes mientras exista un error de almacenamiento.

**Nginx devuelve HTTP 500:** revisar `frontend/nginx.conf`. Debe definir la ruta estática del build dentro del bloque `server`, sin eliminar el proxy `/api`:

```nginx
root /usr/share/nginx/html;
index index.html;
```

Esta corrección debe quedar en el archivo fuente y en una imagen Docker reconstruida; modificar solamente el contenedor no es permanente.

**Un modelo no se encuentra:** revisar los inicializadores y volúmenes de modelos mediante `docker compose ps --all` y `docker compose logs`. No borrar los volúmenes para resolverlo sin antes comprobar su contenido.

**No abre `15173`:** revisar que Docker esté operativo, que `frontend` esté levantado y que ningún otro proceso utilice el puerto.

## 11. Documentación adicional

Para detalles de arquitectura, redes, variables de entorno, persistencia, recuperación y configuración de Windows, consultar también:

```text
docs/DOCKER_WINDOWS.md
```

---

**Nota para el equipo:** esta rama es de desarrollo (`Antony/dockerizacion`). No fusionarla con `main` hasta concluir las pruebas pendientes, especialmente cámara y recorridos E2E.

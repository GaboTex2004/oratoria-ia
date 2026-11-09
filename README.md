# OratoriaAI

**Plataforma Inteligente para el Entrenamiento y Mejora de la Comunicación Oral.**
Continuidad de Ingeniería de Software I / II. Rama de trabajo: `isaias-gutierrez`.

React + Spring Boot + PostgreSQL + FastAPI, con reconocimiento de voz, evaluación
semántica, conversaciones y análisis corporal mediante modelos **locales**. No se
requieren claves de OpenAI, Groq o Gemini ni se realizan cobros.

## Estado y alcance

Incluye registro/login, perfil y contraseña, catálogo por habilidades, grabación y
reproducción privada, transcripción española, métricas temporales y acústicas,
evaluación con rúbrica, simulaciones, improvisación, ejercicios narrativos y
persuasivos, recomendaciones por resultados, progreso, planes/cuotas,
administración e informes imprimibles/PDF. La cámara es opcional y requiere una
acción explícita; solo se conservan sus indicadores.

Incluye además un **checkout académico de tarjetas ficticias**, suscripciones
mensuales Premium, comprobantes, historial, cancelación y renovaciones simuladas.
**MODO DEMOSTRACIÓN — No se realizan cargos reales.** No ingresar tarjetas reales.
Precio predeterminado configurable: Bs 49/mes, obtenido del backend. No existe
integración bancaria ni pasarela comercial. [Guía de pagos](docs/saas/MODO_DEMOSTRACION_PAGOS.md).

Para probar: registrar una cuenta FREE, abrir `/plans`, seleccionar Premium y una
tarjeta ficticia. `/subscription` muestra plan, consumo, historial y cancelación.
ADMIN abre `/admin/payments` para confirmar pendientes y simular el tiempo en dev.
Se requieren APP_PROFILE=dev y BILLING_DEMO_ENABLED=true en backend/.env, preparados
por Initialize-Local.ps1. En producción el simulador rechaza operaciones incluso
si se intenta habilitar el flag. [Contratos](docs/saas/FLUJO_CHECKOUT.md),
[ciclo mensual](docs/saas/ESTADOS_SUSCRIPCION.md) y [pruebas](docs/saas/PRUEBAS_PAGOS.md).

Las métricas y notas son orientativas. No hay modelo supervisado propio entrenado,
validación clínica, reconocimiento de identidad ni detección de emociones. La
precisión corporal con personas reales y la validez de las rúbricas necesitan
validación humana. Ver [la entrega y sus límites](docs/ENTREGA_FINAL_ORATORIAAI.md)
y [los resultados de pruebas](docs/pruebas/INFORME_DE_PRUEBAS.md).

## Arquitectura y puertos

```text
React       http://localhost:5173
  -> Spring http://localhost:8081/api
       -> PostgreSQL 127.0.0.1:5435 / oratoria_db
       -> FastAPI   http://127.0.0.1:8000
            -> Whisper base / MediaPipe locales
            -> Ollama http://127.0.0.1:11434 -> Qwen local
```

Los pesos, credenciales, grabaciones, cachés y herramientas portátiles están excluidos
de Git. [Arquitectura y diagramas](docs/ARQUITECTURA_FINAL.md), [API](docs/API.md),
[base de datos](docs/BASE_DE_DATOS.md), [24 HU Scrum](docs/REQUISITOS_SCRUM.md).

## Requisitos

Probado en Windows 11, Java 21.0.1, Python 3.13.7, Node 24.9.0/npm 11.6.0,
Docker Desktop con Compose 2.39.4 y PostgreSQL 17.6. Maven Wrapper 3.9.16 incluido.
Usar Python 3.13 de 64 bits para el entorno fijado de bibliotecas locales.

Guía estimada para otras computadoras: 16 GB RAM y unos 10 GB libres para el perfil
CPU pequeño; 24 GB RAM y GPU NVIDIA de 6 GB VRAM recomendados para el perfil 4B.
Las mediciones realizadas en este equipo están en [modelos locales](docs/ia/MODELOS_LOCALES.md).

Instalar Git, JDK 21, Python, Node y Docker Desktop desde sus sitios oficiales y
habilitar el motor Linux de Docker. No se instalaron estos productos automáticamente
en esta tarea: ya estaban disponibles. Configurar JAVA_HOME al JDK si Maven no lo encuentra.

```powershell
git --version
java -version
py --version
node --version
npm.cmd --version
docker compose version
```

## Instalación desde una copia nueva

Los ejemplos PowerShell usan `C:\oratoria-ia-main`, la ruta realmente probada.
En otra computadora, sustituir esa ruta por la del clon. No clonar sobre una carpeta
con trabajo existente ni usar git init en un repositorio ya clonado.

```powershell
git clone --branch isaias-gutierrez https://github.com/GaboTex2004/oratoria-ia.git
cd oratoria-ia
```

**Los cambios de finalización permanecen locales hasta que el responsable autorice
su publicación.** El clon remoto solo contendrá esta versión después de ese push.

Desde la raíz de la copia que contenga estos cambios:

```powershell
.\scripts\Initialize-Local.ps1
# En el equipo NVIDIA probado se uso -UseGpu en la primera inicializacion.
# El script conserva valores existentes y no cambia contrasenas ya creadas.
docker compose up -d --wait
cd frontend
npm.cmd ci
cd ..\ai-service
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
cd ..
```

`Initialize-Local.ps1` genera secretos aleatorios y credenciales ADMIN/FREE/PREMIUM en
`backend/.env`, una clave interna compartida en `ai-service/.env` y la URL pública en
`frontend/.env`. No imprime contraseñas. Abrir esos archivos solo localmente. No
copiar el secreto interno o JWT_SECRET a variables VITE_*.

Spring carga `.env` como archivo de propiedades (valores sin comillas); Python usa
python-dotenv. Las variables reales del proceso prevalecen sobre los archivos. Las
plantillas `.env.example` documentan las opciones y no contienen secretos reales.

## Preparar modelos una vez

```powershell
.\scripts\Install-Ollama.ps1
```

Descarga el runtime portátil oficial de unos 1.6 GB y verifica su SHA256. En una
terminal dedicada, dejar funcionando:

```powershell
.\scripts\Start-Ollama.ps1
```

En otra terminal, desde la raíz:

```powershell
.\.tools\ollama\ollama.exe pull qwen2.5:1.5b
cd ai-service
.\.venv\Scripts\python.exe scripts/download_models.py --whisper base --pose
cd ..
```

Para el perfil GPU probado, elegir en su lugar:

```powershell
.\.tools\ollama\ollama.exe pull qwen3:4b-instruct-2507-q4_K_M
```

Y establecer en `ai-service/.env` `OLLAMA_MODEL=qwen3:4b-instruct-2507-q4_K_M` y
`LLM_NUM_GPU=99`. El perfil CPU usa `qwen2.5:1.5b` y `LLM_NUM_GPU=0`.
No hay fallback cloud: un modelo ausente genera un error explícito. El manifiesto
de descarga guarda procedencia y revisión de Whisper/MediaPipe.

## Iniciar la plataforma

PostgreSQL y Ollama deben estar activos. Abrir tres terminales más desde la raíz:

```powershell
# Terminal Spring Boot: compila el JAR e inicia sin logging DEBUG ambiental.
.\scripts\Start-Backend.ps1
```

```powershell
# Terminal FastAPI: solo pesos locales, un worker.
.\scripts\Start-Ai.ps1
```

```powershell
# Terminal React
cd frontend
npm.cmd run dev -- --host 127.0.0.1 --strictPort
```

Abrir **http://localhost:5173**. Las migraciones y semillas se ejecutan al arrancar
Spring. Las cuentas demo solo se inicializan en perfil `dev`, activado por el
inicializador local. El registro normal siempre crea USER + FREE.

Comprobar los procesos HTTP:

```powershell
Invoke-WebRequest http://localhost:8081/api/test -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8000/health -UseBasicParsing
Invoke-WebRequest http://localhost:5173 -UseBasicParsing
```

Un 200 en health no acredita inferencia. [Verificación real de modelos](docs/pruebas/PLAN_DE_PRUEBAS.md).

## Base existente y pruebas aisladas

La primera adopción de una base anterior requiere backup y comparación con V1.
`Initialize-Local.ps1` habilita baseline para esta copia de desarrollo; no usar esa
opción con un esquema desconocido. En una base vacía Flyway ejecuta V1–V3.
Nunca usar `docker compose down -v` para actualizar: elimina datos.

Crear la base de pruebas una sola vez, comprobando primero si existe:

```powershell
docker exec oratoria_postgres psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='oratoria_test'"
# Si el resultado esta vacio:
docker exec oratoria_postgres createdb -U postgres oratoria_test
```

Después:

```powershell
.\scripts\Test-Local.ps1
```

La suite usa TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD y por defecto
`jdbc:postgresql://127.0.0.1:5435/oratoria_test`; nunca hereda DB_URL de desarrollo.
CI prepara una base efímera, ejecuta Java, Python y build/lint/frontend sin descargar
pesos de modelos. El workflow está preparado localmente y no se ejecutó en GitHub
porque no se realizó push.

## Solución de problemas

- **Puerto 5434 ocupado:** esta copia usa 5435; no se detuvo el PostgreSQL instalado
  previamente en Windows. Confirmar `.env` raíz y DB_URL en backend/.env.
- **Docker no conecta:** iniciar Docker Desktop y esperar su motor Linux; luego
  `docker compose ps` y `docker compose logs postgres`.
- **Ollama no responde o falta el modelo:** revisar su terminal, modelo exacto y
  perfil CPU/GPU. La URL de inferencia debe ser HTTP de loopback, sin proxy.
- **CPU lenta:** usar el modelo 1.5B; el modelo 4B en CPU fue mucho más lento en este
  equipo. Aumentar timeouts no convierte un equipo insuficiente en una GPU.
- **401 interno:** las claves INTERNAL_API_KEY de Java y Python deben coincidir.
- **Esquema incompatible:** no borrar tablas ni editar migraciones aplicadas;
  revisar el backup, el log Flyway y la versión de la base.
- **Python Store muestra una advertencia de prefix:** en este equipo el proceso
  funciona y sus pruebas pasan; los scripts verifican el código de salida nativo y
  no confunden stderr con un fallo. Para una instalación nueva se recomienda el
  instalador oficial de Python 3.13 y recrear el entorno virtual si corresponde.
- **Permiso de cámara/micrófono denegado:** concederlo desde el navegador después
  de pulsar la acción correspondiente; no se activa hardware al abrir la página.

[Manual de usuario](docs/MANUAL_USUARIO.md) · [Administración](docs/MANUAL_ADMINISTRACION.md)
· [Datos y consentimiento](docs/ia/DATASET_Y_PROCEDENCIA.md) · [Estándares](docs/ESTANDARES.md)

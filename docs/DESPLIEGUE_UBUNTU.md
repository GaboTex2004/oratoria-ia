# Evaluación académica en Ubuntu

Configuración para una VM Ubuntu x86-64 de 8 GiB, Docker Compose, Nginx del host
y certificado Let's Encrypt existente. La inferencia se ejecuta en el servidor,
sin proveedores externos de IA. La memoria y CPU disponibles limitan la concurrencia;
estos límites son iniciales, no una garantía de capacidad.

## Preparación

Publicar solo TCP 80 y 443; permitir SSH únicamente desde la IP administradora.
Los puertos 5432, 8081, 8000 y 11434 no se publican. El frontend conserva su
publicación exclusiva en 127.0.0.1:15173. Configurar el dominio y certificado
antes de activar el proxy. Los scripts suponen instalados Docker/Compose,
Nginx, Certbot, Python 3 y acceso sudo.

El backend también se conecta a la red `notifications`, con salida a Internet
para entregar Web Push por HTTPS. Esta red no publica ningún puerto y no conecta
PostgreSQL, Ollama ni ai-service a Internet. La lista de destinos admitidos para
las suscripciones sigue controlada por `PUSH_ALLOWED_HOSTS`; la red Docker por sí
sola no filtra dominios de destino. El grupo de seguridad debe permitir esa salida
HTTPS; no se agregan reglas de entrada para las notificaciones.

En Ubuntu, `PUSH_SUBJECT` toma el origen HTTPS público si no está definido. Las
claves VAPID se generan y persisten en la base si se omiten ambas variables
`PUSH_VAPID_PUBLIC_KEY` y `PUSH_VAPID_PRIVATE_KEY`. No hace falta regenerar el `.env`
ni añadir otro contenedor para habilitar la PWA o el asistente de monitoreo.

Desde la raíz de una copia de la versión aprobada, sin copiar archivos .env de
otros equipos:

```bash
python3 scripts/Initialize-Ubuntu.py --domain oratoria-ia.duckdns.org
bash scripts/Deploy-Ubuntu.sh --bootstrap-admin
bash scripts/Activate-Ubuntu.sh
```

El inicializador crea secretos nuevos en .env con permisos 600. No sobrescribe
configuraciones existentes ni imprime valores. El primer despliegue arranca
controladamente con perfil dev para crear el administrador y luego recrea el
backend en demo. Activar el proxy público solo después de terminar ese cambio.
Demo permite el checkout ficticio, pero bloquea el avance artificial del tiempo.
Las credenciales administrativas deben consultarse en privado dentro del servidor.

Los siguientes despliegues usan Deploy-Ubuntu.sh sin --bootstrap-admin. Antes de
actualizar, respaldar datos y mantener una copia de la versión anterior. El script
recarga Nginx tras actualizar el backend para renovar su resolución DNS.
Los volúmenes tienen prefijo oratoriaai-demo; no reutilizan la base del equipo local.
Nunca ejecutar down -v al actualizar. Respaldar base y medios fuera de esta VM.

## Actualizaciones mediante Git

El primer despliegue se transfirió como un archivo tar. Después se conectó la
carpeta existente con `origin/main`, verificando la coincidencia de los archivos
sin reemplazar el `.env` ni recrear contenedores. Git no administra los volúmenes
de PostgreSQL, modelos o grabaciones. La conexión HTTPS con GitHub permite leer
este repositorio público; los cambios se publican desde el equipo de desarrollo.

Tras probar y publicar una corrección en main, respaldar la base y los medios.
En Ubuntu, revisar que `git status --short` esté vacío y ejecutar:

```bash
cd /home/ubuntu/oratoria-ia
git fetch origin
git log --oneline HEAD..origin/main
git diff --stat HEAD..origin/main
git pull --ff-only origin main
```

Actualizar Git por sí solo no reconstruye las imágenes. Para aplicar cambios
de la aplicación o de Compose, programar una breve interrupción y ejecutar:

```bash
bash scripts/Deploy-Ubuntu.sh
curl --fail https://oratoria-ia.duckdns.org/api/test
```

Si cambiaron las plantillas del Nginx del host, aplicar además
`bash scripts/Activate-Ubuntu.sh`. No repetir el inicializador ni
`--bootstrap-admin` en actualizaciones. Mantener el mismo nombre de proyecto
Compose (`oratoriaai-demo`) para conservar los volúmenes utilizados.
No ejecutar `git reset --hard`, `git clean` o `docker compose down -v` para
resolver diferencias: revisar los cambios locales primero. El archivo `.env`
permanece ignorado y cualquier variable nueva requiere revisión en el servidor.

## Verificación y operación

```bash
sudo docker compose -f docker-compose.yml -f docker-compose.deploy.yml ps
sudo docker compose -f docker-compose.yml -f docker-compose.deploy.yml stats --no-stream
curl --fail https://oratoria-ia.duckdns.org/api/test
sudo certbot renew --dry-run --no-random-sleep-on-renew
```

La configuración del host reemplaza los encabezados de IP/protocolo recibidos
del cliente. El Nginx del frontend conserva el protocolo HTTPS enviado por ese
proxy. Ese archivo se utiliza únicamente con el puerto ligado a loopback.
Frontend y API comparten origen, con /api y CORS del dominio exacto. No se
requiere abrir puertos adicionales para micrófono o cámara: el navegador pide
permiso al usar la función desde HTTPS. El host limita solicitudes de login y
registro; responde 429 si se excede el límite. Los logs de contenedores rotan.

La salud HTTP no acredita inferencia. Ejecutar las suites y el recorrido integral
con datos sintéticos en el entorno de pruebas local, nunca contra esta instancia.
En el sitio público, comprobar interfaz, permisos y autenticación sin cargar datos
personales de prueba. El servidor no ofrece alta disponibilidad; controlar disco,
RAM, reinicios, renovación TLS, presupuesto y vencimiento de créditos AWS.

## Evidencia del despliegue inicial (19 de noviembre de 2026)

La versión publicada de la aplicación es d0e1593, integrada en main. Los archivos
de despliegue de este documento se agregaron como configuración separada.
El recorrido local completo `scripts/Test-Docker.ps1 -Models` pasó, incluidos
los 15 casos E2E y las suites de los componentes. En Ubuntu se verificaron
autenticación administrativa, acceso privado sin token (401), planes y salud
de los cinco servicios. Existe un respaldo inicial de PostgreSQL fuera de la VM.

La comprobación directa con datos sintéticos en Ubuntu pasó transcripción,
evaluación de discurso, respuesta de simulación y procesamiento de imagen vacía.
La imagen vacía comprueba la carga y ejecución del modelo corporal, no su precisión
con personas. La evaluación final de simulación falló con JSON incompleto,
también con contexto de 4096 tokens. Es una limitación pendiente del modelo y
su generación; la salud HTTP no la detecta. No considerar esa función validada
en la instancia ni presentar este despliegue como libre de fallos.

## Validación de la actualización PWA (20 de noviembre de 2026)

La actualización incorpora los cambios hasta fbb1000, la salida HTTPS del backend
para Web Push y ajustes de las pruebas para distinguir los mensajes de estado del
nuevo aviso de conexión de la PWA. El arranque de las pruebas espera salud y recarga
Nginx para resolver la dirección actual del backend.

Validación local realizada antes de actualizar Ubuntu:

- `scripts/New-SyntheticAudio.ps1`: WAV sintético de Windows, sin grabaciones humanas.
- `scripts/Test-Docker.ps1 -Models`: pasaron 192 pruebas Java, 66 Python, Ruff,
  29 pruebas frontend, lint y build. Los intentos de E2E completos en Docker fueron
  interrumpidos por el motor local; no se consideran una ejecución integral aprobada.
- El recorrido E2E con Edge en Windows pasó 14 casos, incluido el de modelos reales,
  simulación, cámara sintética, PDF de resultados y eliminación de datos. Falló la
  impresión automática del comprobante mediante `Page.printToPDF` en ese navegador.
- Tras reconstruir `playwright-tests`, los cinco casos de administración y pagos
  pasaron en Chromium de Docker, incluida la generación del PDF del comprobante.
  Junto con la ejecución anterior, quedan cubiertos los 15 casos ajenos a la PWA.
- Los tres casos de `e2e/pwa.spec.ts` pasaron aparte con `E2E_PWA=1` y Edge:
  manifiesto e iconos, apertura sin conexión, sesión sin cachear `/api` y ancho móvil.
- El asistente de monitoreo respondió con Qwen y métricas sintéticas, sin consultar
  una base de datos: 16,14 s, con evidencia limitada a las métricas recibidas.
- El backend local alcanzó por HTTPS el servicio FCM (HTTP 404 en su ruta raíz,
  esperado para esta comprobación de conectividad, sin enviar notificaciones).

Se validaron respaldos de PostgreSQL y medios antes de la actualización y se guardó
una copia fuera de la VM. La entrega push en un teléfono requiere permiso del
usuario y sigue pendiente de comprobación manual. Estos resultados no garantizan
precisión de los modelos ni capacidad para múltiples inferencias concurrentes.

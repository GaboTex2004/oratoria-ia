# Administración y operación local

La instalación y comandos están en [README](../README.md). Requisitos: Windows con
PowerShell, Git, Node 24, Java 21, Python 3.13 y Docker Desktop con motor Linux.
Esta entrega fue ejecutada sobre Windows 11; otros sistemas necesitan adaptar los
scripts PowerShell y la voz del sistema. El perfil CPU no requiere GPU NVIDIA.

`scripts/Initialize-Local.ps1` crea configuración privada con contraseñas aleatorias.
`-UseGpu` selecciona el perfil recomendado para el equipo probado. No imprime claves
ni sustituye contraseñas existentes. Mantener iguales las claves internas de Java y
Python. Nunca agregar `.env`, audio, modelos ni bases al control de versiones.

ADMIN, FREE y PREMIUM se inicializan por variables de entorno solo con APP_PROFILE
dev. Leer sus correos/claves localmente en `backend/.env`; no pegarlos en reportes.
El seed es idempotente y no cambia la contraseña de cuentas preexistentes. Las
cuentas de pruebas de navegador usan nombres y grabaciones sintéticos.

Entrar como ADMIN en `/admin`: revisar conteos y usuarios, habilitar/deshabilitar
USER, asignar FREE/PREMIUM, crear/editar/desactivar ejercicios. La recuperación
manual exige verificar identidad por un canal adecuado antes de establecer una
clave temporal de al menos 12 caracteres. No se ofrece elevación pública a ADMIN;
no se permite deshabilitar al administrador por esa pantalla. Se preserva el
historial de ejercicios desactivados.

Antes de migraciones, respaldar PostgreSQL con `pg_dump` y verificar restauración
en una base separada. No usar `docker compose down -v`: borra el volumen. Flyway
V1–V3 está probado en una base nueva y por adopción de la base existente. La opción
baseline solo debe habilitarse al adoptar un esquema comprobado; no corrige un
esquema incompatible. Ver [base de datos](BASE_DE_DATOS.md).

`oratoria_test` es exclusiva de pruebas: nunca apuntar TEST_DB_URL a la base de
desarrollo. La suite puede limpiar datos. Mantener los servicios de desarrollo y
pruebas separados. Los scripts comprueban códigos de salida; no ejecutar dos
compilaciones Maven simultáneas ni recompilar un JAR abierto en Windows.

Diagnóstico: comprobar puertos 5435/11434/8000/8081/5173; revisar la terminal del
servicio que falla. No activar DEBUG con datos reales: DTO sensibles están
redactados, pero un servidor con trazas indiscriminadas sigue siendo inadecuado.
El inicio del backend fuerza INFO y debug=false.

La entrega es para demostración local. Exponerla a Internet requiere revisión de
TLS, firewall, gestión de secretos, recuperación con identidad verificable,
retención, monitoreo y capacidad. No se ejecutó esa publicación ni una auditoría
de penetración. CI está preparada pero no ejecutada en GitHub al no existir push.

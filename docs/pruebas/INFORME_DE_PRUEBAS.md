# Informe de pruebas ejecutadas

Fecha de ejecución: 2026-11-08, America/La_Paz. Rama `isaias-gutierrez`.
No se calcula porcentaje de cobertura a partir del número de pruebas.

## Resultados automatizados

| Verificación | Resultado real | Evidencia |
|---|---|---|
| Java / Spring / PostgreSQL | 49 ejecutadas, cero fallos/errores/omitidas | Log local `.tools/final-checks.log`; integración con oratoria_test |
| Python | 50 aprobadas | Mismo log; pytest sin pesos, 1.05 s en esta ejecución |
| Ruff | Sin errores | Mismo script Test-Local.ps1 |
| Frontend unitarias | 14 aprobadas | Node test; mismo log |
| Navegador Edge, última ejecución | 8 aprobadas en 2.6 min; cero fallos | evidencias/navegador-final.txt; incluye cámara ficticia y modelos reales |
| ESLint + TypeScript/Vite | Aprobados | Build con 82 módulos y fuentes locales |
| Migración desde base vacía | Aprobada | BackendApplicationTests en oratoria_migration_test; V1–V3 |
| Adopción de esquema existente | Aprobada | Base de pruebas y arranque dev con Flyway/validate |
| npm audit | Cero vulnerabilidades notificadas después de actualizar | Inicialmente 13: tres altas y diez moderadas |
| pip check | Sin conflictos de dependencias | No constituye análisis de CVE |
| Modelos CPU 1.5B | Cinco comprobaciones aprobadas | evidencias/modelos-cpu-sinteticos.json |
| Modelos GPU 4B | Cinco comprobaciones aprobadas | evidencias/modelos-gpu-sinteticos.json |

Los JSON contienen salidas de modelos sobre texto/audio sintéticos y una imagen
vacía. Las notas de estas fixtures no son mediciones sobre alumnos. La rúbrica se
versionó posteriormente a v2.0-local; el recorrido de navegador usa esa versión.

## Navegador e integración real

Seis tamaños: 320, 375, 580, 768, 980 y 1440 píxeles. Incluye login/registro y nueve
pantallas autenticadas, navegación móvil y ausencia de desborde horizontal. ADMIN
usa autenticación real y crea un ejercicio inactivo marcado como prueba.

El recorrido con modelos registra una cuenta sintética, graba mediante MediaRecorder
con WAV de voz SAPI ficticia, transcribe/evalúa por los servicios reales, exporta
informe y progreso a PDF, conversa dos turnos y evalúa la simulación; captura cámara
ficticia y solicita borrado de contenido. El estado final de la última repetición
se registra en `evidencias/navegador-final.txt` y en la entrega final.

Las capturas/PDF locales están en `.tools/browser-results`. No se incluye la captura
ADMIN en Git porque podría mostrar cuentas locales. Los tests de navegador crean
fixtures persistidas: sus conteos no deben presentarse como usuarios reales.

## Fallos encontrados y correcciones

1. Tres errores Java de clases antiguas en target: `clean test` reconstruyó las
   clases; después se preservaron y ejecutaron los tests existentes.
2. Catálogo/escenarios y cuota cambiaron: se actualizaron expectativas al contrato
   real de 20 ejercicios, ocho escenarios y sesiones creadas en el mes UTC.
3. La prueba Python esperaba rúbrica v1: se actualizó a v2.0-local. Ejecución final
   de las 50 pruebas aprobada, sin eliminar el control de versión.
4. Importar fuentes por CSS dejó la página vacía en la primera corrida: Vite emitía
   ENOENT; la importación directa desde main.tsx corrigió seis fallos de navegador.
5. Dos anchos móviles desbordaban mientras cargaban los iconos: caja de icono de
   tamaño fijo y nueva verificación a 320 y 375 aprobada.
6. El selector de práctica corporal incluía texto de sus opciones en su nombre
   accesible: se añadió aria-label preciso.
7. Primera corrida integral final: siete tests aprobados y uno fallido. La parte
   corporal devolvió 422: el nuevo cliente Java negociaba h2c; se corrigió a HTTP/1.1
   como los otros clientes. La repetición completa aprobó los ocho tests y la
   llamada corporal devolvió 200.
8. La primera inicialización GPU agotó 120 s; se ajustaron perfiles/timeouts. El
   modelo pequeño con prompts iniciales produjo JSON/rol incorrectos: se redujeron
   prompts y fijó esquema; luego pasó los cinco controles. El modelo 4B en CPU fue
   demasiado lento y se detuvo esa medición; no se lo recomienda para CPU.
9. Un intento de empaquetar con el JAR abierto falló en Windows; se detuvo únicamente
   el proceso local identificado y se reconstruyó/inició correctamente.
10. La comprobación de whitespace señala una línea vacía final en el dump V1 y
    un espacio del texto oficial de licencia Inter. Se conservaron el esquema ya
    aplicado y la licencia original; el chequeo excluyendo esos dos recursos no
    detecta problemas en código propio. No se alteró una migración aplicada por estilo.

## Límites de la evidencia

No se midió cobertura de líneas/ramas. No hubo corpus humano, medición WER,
concordancia entre evaluadores, validación científica de rúbrica, postura humana,
dispositivos físicos móviles, prueba de carga ni auditoría completa de seguridad.
El test de Pose con imagen vacía comprueba ausencia de persona y contrato; una
cámara ficticia no certifica exactitud corporal. No se entrenó un modelo propio.
CI existe como archivo local y no se ejecutó en GitHub. No se hizo push.

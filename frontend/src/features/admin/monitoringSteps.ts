/** Pasos cortos que el monitoreo muestra junto a cada alerta (el código lo calcula el backend con reglas fijas). */
const STEPS: [RegExp, string[]][] = [
  [/^SERVICIO_BASE_DATOS_DOWN$|^DATABASE_QUERY_FAILED$/, [
    'Revisa que el contenedor de PostgreSQL esté encendido (docker compose ps).',
    'Si está detenido, levántalo con docker compose up -d postgres.',
    'Vuelve a este panel: en unos segundos debe mostrarse como Operativo.',
  ]],
  [/^SERVICIO_IA_DOWN$/, [
    'Revisa que el servicio de IA y Ollama estén encendidos (docker compose ps).',
    'Si alguno está detenido, levántalo con docker compose up -d ai-service ollama.',
    'Mientras tanto las prácticas nuevas quedan en cola; no hace falta repetirlas.',
  ]],
  [/^ALMACENAMIENTO_AUDIO_DOWN$/, [
    'Revisa que la carpeta de audios exista y que el disco tenga espacio libre.',
    'Reinicia el backend si el disco ya está bien.',
  ]],
  [/_STUCK$/, [
    'Revisa que el servicio de IA esté Operativo en el grupo Servicios.',
    'Si está lento, espera unos minutos: el modelo atiende de a una práctica.',
    'Si sigue igual, reinicia el servicio de IA (docker compose restart ai-service).',
  ]],
  [/_FAILING$/, [
    'Abre la pestaña Bitácora para ver qué prácticas fallaron.',
    'Comprueba que el servicio de IA esté Operativo.',
    'Pide a los usuarios afectados que repitan la práctica.',
  ]],
  [/_QUEUE$/, [
    'Hay muchas prácticas esperando: es normal en horas de mucho uso.',
    'Si la cola no baja en 10 minutos, revisa el servicio de IA.',
  ]],
  [/^LOGIN_FAILURES$/, [
    'Abre la pestaña Bitácora y mira desde qué cuentas fallan los ingresos.',
    'Si es una sola cuenta, avisa a esa persona o desactívala en Usuarios.',
  ]],
  [/^MEDIA_DELETION_DELAYED$/, [
    'Revisa que el backend esté encendido: es quien borra los audios.',
    'Comprueba que la carpeta de audios tenga permisos de escritura.',
  ]],
  [/^PUSH_FAILING$/, [
    'Revisa las variables VAPID y PUSH_SUBJECT del servidor.',
    'Envía una notificación de prueba desde tu perfil para comprobarlo.',
  ]],
  [/^BACKEND_MEMORY$/, [
    'Reinicia el backend en un horario sin prácticas en curso.',
    'Si se repite, aumenta la memoria asignada al contenedor.',
  ]],
]

export function stepsFor(code: string): string[] {
  return STEPS.find(([pattern]) => pattern.test(code))?.[1] ?? ['Abre el panel de Monitoreo y usa el asistente de IA para ver qué revisar.']
}

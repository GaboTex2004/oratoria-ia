/**
 * Guías del asistente: cada una responde una pregunta con pasos cortos que usan los nombres reales de menús y botones.
 * La búsqueda es por palabras clave (sin IA), así que responde al instante y funciona aunque el modelo local esté ocupado.
 */
export interface Guide {
  id: string
  question: string
  steps: string[]
  keywords: string[]
  /** Pantalla donde esta guía es la ayuda principal; "/admin?tab=x" para una pestaña de Administración. */
  screen?: string
  link?: { to: string; label: string }
  /** Quién la ve: sin valor, cualquier cuenta; 'guest' antes de iniciar sesión; 'all' siempre; 'admin' solo administradores. */
  audience?: 'guest' | 'all' | 'admin'
}

export const GUIDES: Guide[] = [
  {
    id: 'ingresar', question: '¿Cómo entro a mi cuenta?', screen: '/login', audience: 'guest',
    keywords: ['entrar', 'ingresar', 'iniciar sesion', 'login', 'acceder', 'ayuda', 'como se usa'],
    steps: [
      'Escribe tu correo electrónico y tu contraseña.',
      'Toca «Iniciar sesión».',
      'Si todavía no tienes cuenta, toca «Crear cuenta».',
    ],
  },
  {
    id: 'registro', question: '¿Cómo creo una cuenta?', screen: '/register', audience: 'guest',
    keywords: ['crear cuenta', 'crear una cuenta', 'creo una cuenta', 'creo mi cuenta', 'registr', 'nueva cuenta', 'cuenta nueva'],
    steps: [
      'En la pantalla de ingreso toca «Crear cuenta».',
      'Completa nombre, apellido, correo y contraseña (dos veces).',
      'Acepta los términos y toca «Crear mi cuenta». Empiezas con el plan FREE.',
    ],
    link: { to: '/register', label: 'Ir a Crear cuenta' },
  },
  {
    id: 'inicio', question: '¿Por dónde empiezo?', screen: '/home',
    keywords: ['empiezo', 'empezar', 'inicio', 'ayuda', 'que hago', 'como funciona', 'como se usa', 'guia', 'primeros pasos'],
    steps: [
      'Abre Entrenamientos en el menú y elige un ejercicio.',
      'Graba tu discurso con el micrófono y guarda la sesión.',
      'Mira tu evaluación en Resultados.',
      'Sigue tu avance en Progreso y en Mi siguiente práctica.',
    ],
    link: { to: '/training', label: 'Ir a Entrenamientos' },
  },
  {
    id: 'practica', question: '¿Cómo hago una práctica?', screen: '/training',
    keywords: ['practica', 'practicar', 'grabar', 'grabacion', 'entrenar', 'entrenamiento', 'ejercicio', 'microfono', 'discurso', 'hablar'],
    steps: [
      'Abre Entrenamientos y elige una habilidad y un ejercicio (o toca «Sortear tema de improvisación»).',
      'Lee el objetivo y la consigna, y toca «Practicar ahora».',
      'Toca «Comenzar grabación» y permite el uso del micrófono.',
      'Habla y, al terminar, toca «Detener grabación».',
      'Toca «Finalizar y guardar sesión». El análisis se hace solo y aparece en Resultados.',
    ],
    link: { to: '/training', label: 'Ir a Entrenamientos' },
  },
  {
    id: 'resultados', question: '¿Dónde veo mis resultados?', screen: '/results',
    keywords: ['resultado', 'nota', 'analisis', 'evaluacion', 'muletilla', 'pausa', 'ritmo', 'puntaje', 'calificacion', 'wpm'],
    steps: [
      'Abre Resultados en el menú y elige la sesión.',
      'Espera a que termine el análisis; la primera vez puede tardar unos minutos.',
      'Revisa el ritmo (palabras por minuto), las pausas, las muletillas y la evaluación del contenido.',
      'Toca «Informe / PDF» si quieres guardarlo o imprimirlo.',
    ],
    link: { to: '/results', label: 'Ir a Resultados' },
  },
  {
    id: 'sesiones', question: '¿Cómo veo o borro mis sesiones?', screen: '/sessions',
    keywords: ['sesion', 'historial', 'grabaciones', 'borrar', 'eliminar', 'privacidad', 'audio guardado'],
    steps: [
      'Abre Mis sesiones en el menú.',
      'Ahí están todas tus prácticas con su fecha y duración; «Nueva grabación» inicia otra.',
      'Para borrar una, toca «Eliminar datos»: se borra el audio y el resultado.',
    ],
    link: { to: '/sessions', label: 'Ir a Mis sesiones' },
  },
  {
    id: 'progreso', question: '¿Cómo veo mi progreso?', screen: '/progress',
    keywords: ['progreso', 'evolucion', 'avance', 'mejorado', 'mejore', 'comparar', 'estadistica'],
    steps: [
      'Abre Progreso en el menú.',
      'Compara tus prácticas a lo largo del tiempo.',
      'Toca «Ver resultados» en una práctica para ver su detalle.',
      'Usa «Imprimir evolución / PDF» para guardarlo.',
    ],
    link: { to: '/progress', label: 'Ir a Progreso' },
  },
  {
    id: 'simulacion', question: '¿Cómo funciona una simulación?', screen: '/simulations',
    keywords: ['simulacion', 'conversacion', 'entrevista', 'escenario', 'turno', 'dialogo'],
    steps: [
      'Abre Simulaciones, elige un escenario y una dificultad, y toca «Iniciar».',
      'Escucha o lee lo que dice el personaje y toca «Grabar respuesta».',
      'Toca «Detener» y luego «Enviar turno» (o «Regrabar» si no te gustó).',
      'Después de al menos dos turnos, toca «Finalizar y evaluar conversación» para ver la retroalimentación.',
    ],
    link: { to: '/simulations', label: 'Ir a Simulaciones' },
  },
  {
    id: 'corporal', question: '¿Cómo analizo mi postura con la cámara?', screen: '/body',
    keywords: ['camara', 'cuerpo', 'postura', 'corporal', 'gestos', 'video', 'mirada'],
    steps: [
      'Abre Análisis corporal y entra a «Práctica con audio y cámara».',
      'Elige un ejercicio y permite el micrófono y la cámara.',
      'Toca «Grabar audio y cámara» y, al terminar, «Detener audio y cámara».',
      'Toca «Guardar y analizar audio y cuerpo». No se guarda video, solo los indicadores.',
    ],
    link: { to: '/body', label: 'Ir a Análisis corporal' },
  },
  {
    id: 'voz', question: '¿Cómo ajusto mi voz y entonación?',
    keywords: ['voz', 'entonacion', 'tono', 'teleprompter', 'velocidad', 'enfasis'],
    steps: [
      'En Entrenamientos, abre «Ajusta tu voz y entonación».',
      'Toca «Grabar discurso inicial» y permite el micrófono.',
      'Elige «Ajustar y practicar otra vez» y escoge un ritmo o una duración.',
      'Sigue el teleprompter mientras grabas el nuevo intento.',
    ],
    link: { to: '/training', label: 'Ir a Entrenamientos' },
  },
  {
    id: 'siguiente', question: '¿Qué debería practicar ahora?', screen: '/recommendations',
    keywords: ['recomendacion', 'recomienda', 'siguiente', 'sugerencia', 'que practico', 'que deberia'],
    steps: [
      'Abre Mi siguiente práctica en el menú.',
      'Ahí verás qué conviene practicar según tus resultados reales.',
      'Toca el ejercicio sugerido para empezarlo.',
    ],
    link: { to: '/recommendations', label: 'Ir a Mi siguiente práctica' },
  },
  {
    id: 'estilos', question: '¿Qué son los estilos de comunicación?', screen: '/styles',
    keywords: ['estilo'],
    steps: [
      'Abre Estilos de comunicación en el menú.',
      'Lee cada estilo y toca «Elegir como mi estilo» en el que quieras trabajar.',
      'Toca «Practicar» en uno de sus ejercicios.',
    ],
    link: { to: '/styles', label: 'Ir a Estilos' },
  },
  {
    id: 'reporte', question: '¿Cómo descargo un reporte en PDF?', screen: '/reports',
    keywords: ['reporte', 'informe', 'pdf', 'imprimir', 'descargar', 'desempeno'],
    steps: [
      'Abre Reporte de desempeño en el menú.',
      'Revisa el resumen de tus prácticas.',
      'Toca «Imprimir / guardar PDF» y, en la ventana del navegador, elige «Guardar como PDF».',
    ],
    link: { to: '/reports', label: 'Ir a Reporte de desempeño' },
  },
  {
    id: 'perfil', question: '¿Cómo cambio mis datos o mi contraseña?', screen: '/profile',
    keywords: ['perfil', 'nombre', 'contrasena', 'clave', 'password', 'mis datos'],
    steps: [
      'Abre Perfil en el menú.',
      'Corrige tu nombre y toca «Guardar perfil».',
      'Para la contraseña, escribe la actual y la nueva, y toca «Cambiar contraseña».',
      'Después vas a tener que iniciar sesión de nuevo.',
    ],
    link: { to: '/profile', label: 'Ir a Perfil' },
  },
  {
    id: 'notificaciones', question: '¿Cómo activo las notificaciones?',
    keywords: ['notificacion', 'aviso', 'recordatorio', 'push', 'alerta'],
    steps: [
      'Abre Perfil y baja hasta Notificaciones.',
      'Toca «Activar notificaciones» y acepta el permiso del navegador.',
      'Elige si quieres el aviso de resultado listo y el recordatorio diario.',
      'Toca «Enviar notificación de prueba» para comprobarlo.',
    ],
    link: { to: '/profile', label: 'Ir a Perfil' },
  },
  {
    id: 'premium', question: '¿Cómo paso a Premium?', screen: '/plans',
    keywords: ['plan', 'premium', 'pagar', 'pago', 'limite', 'cuota', 'free', 'gratis', 'precio'],
    steps: [
      'El plan FREE permite 5 prácticas y 1 simulación por mes; Premium no tiene límite.',
      'Abre Planes y toca «Seleccionar Premium».',
      'Completa los datos de la tarjeta de prueba y toca «Confirmar pago simulado» (no se cobra nada).',
      'En Mi suscripción ves tus comprobantes y puedes cancelar la renovación.',
    ],
    link: { to: '/plans', label: 'Ir a Planes' },
  },
  {
    id: 'suscripcion', question: '¿Cómo veo o cancelo mi suscripción?', screen: '/subscription',
    keywords: ['suscripcion', 'cancelar', 'renovacion', 'comprobante', 'factura', 'mi plan'],
    steps: [
      'Abre Planes y toca «Mi suscripción e historial».',
      'Ahí ves tu plan, hasta cuándo dura y tus operaciones; «Ver comprobante» abre cada pago.',
      'Para no renovar, toca «Cancelar renovación simulada». Sigues con Premium hasta el vencimiento.',
    ],
    link: { to: '/subscription', label: 'Ir a Mi suscripción' },
  },
  {
    id: 'checkout', question: '¿Cómo completo el pago?', screen: '/checkout',
    keywords: ['checkout', 'tarjeta', 'completar pago', 'pago de prueba'],
    steps: [
      'Elige una de las tarjetas ficticias de la lista.',
      'Toca «Confirmar pago simulado» (no se cobra nada real).',
      'Si queda pendiente, toca «Actualizar estado» o espera la confirmación del administrador.',
    ],
    link: { to: '/subscription', label: 'Ir a Mi suscripción' },
  },
  {
    id: 'instalar', question: '¿Cómo instalo la app?', audience: 'all',
    keywords: ['instalar', 'app', 'aplicacion', 'celular', 'telefono', 'escritorio', 'pwa', 'iphone', 'android'],
    steps: [
      'En Chrome o Edge (Android o computadora), toca «Instalar» en la tarjeta «Instala OratoriaAI» o en el menú, en «Instalar aplicación».',
      'En iPhone o iPad (Safari), toca Compartir y luego «Agregar a inicio».',
      'Queda un ícono como cualquier app, con tu misma cuenta.',
    ],
  },
  {
    id: 'acceso', question: 'Olvidé mi contraseña', audience: 'all',
    keywords: ['olvide', 'recuperar', 'no puedo entrar', 'bloqueado', 'no recuerdo'],
    steps: [
      'Pide ayuda al administrador de la plataforma.',
      'Él verifica tu identidad y te da una contraseña temporal.',
      'Entra con ella y cámbiala en Perfil.',
    ],
  },
  {
    id: 'admin-resumen', question: '¿Qué muestra el resumen de administración?', screen: '/admin?tab=resumen', audience: 'admin',
    keywords: ['resumen', 'estadisticas', 'cuantos usuarios', 'uso de la plataforma', 'administracion'],
    steps: [
      'Abre Administración → Resumen.',
      'Arriba ves usuarios registrados, prácticas y simulaciones del mes.',
      'Abajo, el uso de los últimos 6 meses, los ejercicios más usados y las dificultades frecuentes.',
    ],
    link: { to: '/admin?tab=resumen', label: 'Ir a Resumen' },
  },
  {
    id: 'admin-planes', question: '¿Cómo cambio los límites de un plan?', screen: '/admin?tab=planes', audience: 'admin',
    keywords: ['limites del plan', 'editar plan', 'cambiar plan', 'funciones incluidas', 'planes'],
    steps: [
      'Abre Administración → Planes.',
      'Cambia el nombre visible, los límites de uso o las funciones incluidas.',
      'Toca «Guardar plan» (o «Descartar cambios» para volver atrás).',
    ],
    link: { to: '/admin?tab=planes', label: 'Ir a Planes' },
  },
  {
    id: 'admin-reportes', question: '¿Cómo veo los reportes de uso?', screen: '/admin?tab=reportes', audience: 'admin',
    keywords: ['reportes de uso', 'reportes', 'muletillas mas usadas', 'ejercicios completados', 'periodo'],
    steps: [
      'Abre Administración → Reportes.',
      'Elige el periodo de los reportes.',
      'Revisa los ejercicios completados, las evaluaciones, las dificultades y las muletillas más usadas.',
    ],
    link: { to: '/admin?tab=reportes', label: 'Ir a Reportes' },
  },
  {
    id: 'admin-bitacora', question: '¿Cómo reviso quién hizo qué?', screen: '/admin?tab=bitacora', audience: 'admin',
    keywords: ['bitacora', 'auditoria', 'quien hizo', 'registro de acciones', 'historial de acciones'],
    steps: [
      'Abre Administración → Bitácora.',
      'Elige la acción y las fechas «Desde» y «Hasta», y toca «Filtrar».',
      'Usa «Siguiente» y «Anterior» para recorrer los registros; «Limpiar» quita los filtros.',
    ],
    link: { to: '/admin?tab=bitacora', label: 'Ir a Bitácora' },
  },
  {
    id: 'admin-usuarios', question: '¿Cómo administro usuarios?', screen: '/admin?tab=usuarios', audience: 'admin',
    keywords: ['usuarios', 'habilitar', 'deshabilitar', 'restablecer', 'asignar plan', 'contrasena temporal'],
    steps: [
      'Abre Administración → Usuarios y busca por nombre o correo.',
      'Usa «Habilitar» o «Deshabilitar» para la cuenta, y elige FREE o PREMIUM en Plan.',
      'Para recuperar un acceso, verifica la identidad, escribe una contraseña temporal (12 caracteres o más) y toca «Restablecer contraseña».',
    ],
    link: { to: '/admin?tab=usuarios', label: 'Ir a Usuarios' },
  },
  {
    id: 'admin-catalogo', question: '¿Cómo creo un ejercicio?', screen: '/admin?tab=catalogo', audience: 'admin',
    keywords: ['crear ejercicio', 'nuevo ejercicio', 'catalogo', 'categoria', 'editar ejercicio'],
    steps: [
      'Abre Administración → Ejercicios y categorías.',
      'En «Crear ejercicio» completa código, título, descripción, objetivo e instrucciones.',
      'Marca si está disponible para practicar y toca «Guardar».',
    ],
    link: { to: '/admin?tab=catalogo', label: 'Ir a Ejercicios' },
  },
  {
    id: 'admin-respaldos', question: '¿Cómo hago un respaldo?', screen: '/admin?tab=respaldos', audience: 'admin',
    keywords: ['respaldo', 'backup', 'restaurar', 'copia de seguridad'],
    steps: [
      'Abre Administración → Respaldos.',
      'Toca «Generar respaldo ahora» (puedes agregar una nota).',
      'Usa «Descargar» para guardarlo fuera del servidor.',
      'Para volver atrás, toca «Restaurar» en un respaldo o «Restaurar desde un archivo».',
    ],
    link: { to: '/admin?tab=respaldos', label: 'Ir a Respaldos' },
  },
  {
    id: 'admin-monitoreo', question: '¿Cómo reviso el estado de la plataforma?', screen: '/admin?tab=monitoreo', audience: 'admin',
    keywords: ['monitoreo', 'estado', 'servicio', 'caido', 'falla', 'error', 'lento', 'trabado'],
    steps: [
      'Mira el estado arriba de este asistente: se revisa solo cada 15 segundos.',
      'Abre Administración → Monitoreo para ver las alertas y las cifras.',
      'Cada alerta trae los pasos para resolverla.',
      'Toca «Analizar estado» si quieres un diagnóstico de la IA local.',
    ],
    link: { to: '/admin?tab=monitoreo', label: 'Ir a Monitoreo' },
  },
  {
    id: 'admin-aviso', question: '¿Cómo envío un aviso a todos?', screen: '/admin?tab=sistema', audience: 'admin',
    keywords: ['aviso a todos', 'anuncio', 'comunicado', 'notificar a todos', 'mensaje a todos'],
    steps: [
      'Abre Administración → Sistema.',
      'Escribe el «Aviso para todos los usuarios» para que se vea dentro de la app.',
      'O en «Notificación push a todos» escribe título y mensaje, y toca «Enviar notificación».',
    ],
    link: { to: '/admin?tab=sistema', label: 'Ir a Sistema' },
  },
  {
    id: 'admin-pagos', question: '¿Cómo confirmo un pago pendiente?', screen: '/admin/payments', audience: 'admin',
    keywords: ['confirmar pago', 'pago pendiente', 'pagos', 'comprobante'],
    steps: [
      'Abre Administración → Usuarios y toca «Administrar pagos simulados».',
      'Busca la orden pendiente.',
      'Toca «Confirmar» para aprobarla.',
    ],
    link: { to: '/admin/payments', label: 'Ir a Pagos' },
  },
]

export const normalize = (text: string) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export type Role = 'GUEST' | 'USER' | 'ADMIN'

export function guidesFor(role: Role): Guide[] {
  return GUIDES.filter(guide => guide.audience === 'all'
    || (role === 'GUEST' ? guide.audience === 'guest' : guide.audience === undefined || (guide.audience === 'admin' && role === 'ADMIN')))
}

/** La guía que mejor responde la pregunta: suma puntos por cada palabra clave encontrada (las de varias palabras valen más). Cada palabra clave debe empezar una palabra, para que «pago» no coincida con «apagar». */
export function findGuide(question: string, role: Role): Guide | null {
  const text = ` ${normalize(question).replace(/[^a-z0-9ñ ]/g, ' ')} `
  let best: Guide | null = null
  let bestScore = 0
  for (const guide of guidesFor(role)) {
    const score = guide.keywords.reduce((total, keyword) => text.includes(` ${keyword}`) ? total + keyword.split(' ').length : total, 0)
    // Ante un empate gana la guía de administración: es más específica para quien puede verla.
    if (score > bestScore || (score === bestScore && score > 0 && guide.audience === 'admin')) { best = guide; bestScore = score }
  }
  return best
}

/** La guía de la pantalla actual: pathname y, en Administración, la pestaña (Resumen si no hay ninguna). */
export function guideForScreen(pathname: string, search: string, role: Role): Guide {
  const tab = new URLSearchParams(search).get('tab')
  const screen = pathname === '/admin' ? `/admin?tab=${tab ?? 'resumen'}` : pathname
  const guides = guidesFor(role)
  return guides.find(guide => guide.screen === screen)
    // Pantallas con identificador, como /checkout/123 o /reports/abc.
    ?? guides.find(guide => guide.screen && !guide.screen.includes('?') && guide.screen !== '/home' && screen.startsWith(guide.screen + '/'))
    ?? guides.find(guide => guide.id === (role === 'GUEST' ? 'ingresar' : 'inicio'))!
}

# Manual de usuario

## Ajusta tu voz y entonación

En Entrenamientos, abrir «Ajusta tu voz y entonación». Autorizar el micrófono,
grabar el discurso inicial y detenerlo para transcribirlo con Whisper local.
La envolvente de intensidad y la curva tonal se estiman en el navegador; no
son mediciones calibradas ni permiten diagnosticar emociones. Las muletillas
se detectan en la transcripción, no en la forma de la onda.

Elegir «Ajustar y practicar otra vez». Seleccionar un ritmo o una duración:
el objetivo puede ser menor o mayor que el discurso original. La referencia de
escucha ajusta la velocidad conservando el tono si el navegador lo soporta;
no sintetiza énfasis ni una voz nueva. El objetivo respeta el límite de grabación
y velocidades entre 0,25× y 4×. Se pueden marcar palabras clave para enfatizar.

El teleprompter resalta palabras según las marcas temporales escaladas de la
referencia (o una distribución estimada si no hay marcas). No sigue la voz en
tiempo real. El color avanza de izquierda a derecha dentro de cada palabra,
conserva coloreadas las anteriores y desplaza suavemente las líneas. Durante
las pausas no se rellena la siguiente palabra. Puede pausarse sin detener el audio. La referencia gris aparece
reducida y ajustada al tiempo elegido; la captura actual es azul, en la misma
escala. Llegar al tiempo objetivo no detiene la grabación: se puede continuar
hasta el máximo configurado. Se conservan como máximo dos intentos en memoria.
Al cerrar o recargar se descartan; no se crean sesiones ni historial. Si falla
la transcripción, puede reintentarse con el audio ya capturado.

## Audio y cámara en Análisis corporal

En Análisis corporal, abrir «Práctica con audio y cámara», elegir un ejercicio y
autorizar ambos dispositivos. Pulsar «Grabar audio y cámara» y después «Detener
audio y cámara». Revisar el audio antes de «Guardar y analizar audio y cuerpo».
Se crea una sesión nueva, sujeta al plan y cuota habituales, sin reemplazar otras
grabaciones. Se toman hasta doce imágenes distribuidas durante la práctica; no se
guarda video. La transcripción, las muletillas, el ritmo y la evaluación aparecen
junto al resultado corporal y también en Resultados. Si falla uno de los envíos,
«Reintentar pendientes» conserva el resultado exitoso y reenvía solo el pendiente.
No recargar la página antes de guardar o reintentar: la captura pendiente está en
memoria. La opción «Solo cámara» sigue disponible para prácticas existentes.

Abrir http://localhost:5173 con los servicios del README iniciados. Las cuentas
de demostración se crean únicamente en desarrollo; sus claves están en el archivo
privado `backend/.env`. También se puede registrar una cuenta nueva FREE.

1. **Perfil:** iniciar sesión, corregir nombres o cambiar contraseña indicando la
   actual. Cambiarla invalida los tokens anteriores y exige volver a iniciar sesión.
2. **Entrenamiento:** buscar una habilidad, elegir categoría y ejercicio; revisar
   objetivo, dificultad y consigna. El sorteo de improvisación elige un tema real
   del catálogo. Iniciar práctica, permitir micrófono y grabar una intervención.
3. **Resultados:** guardar audio y solicitar análisis. Esperar la transcripción
   Whisper y evaluación local. Un primer análisis puede tardar unos minutos por
   la carga del modelo. No repetir clics mientras se procesa. Con evidencia escasa
   no se debe interpretar una ausencia de nota como una puntuación cero.
4. **Indicadores:** revisar palabras por minuto, pausas, muletillas y mediciones
   acústicas. dBFS es nivel digital relativo; no representa volumen físico en dB
   SPL. F0 puede faltar y no es un diagnóstico de voz.
5. **Simulaciones:** elegir escenario, responder por audio y enviar cada turno.
   Finalizar tras al menos dos intervenciones suficientes para ver retroalimentación.
   Esta nota se mantiene separada de las prácticas. Escuchar voz es opcional y
   requiere una voz española local del navegador/sistema.
6. **Cámara:** seleccionar una práctica propia y marcar consentimiento. Activar
   cámara, revisar encuadre y capturar ocho imágenes. Apagarla cuando se desee.
   Solo se guardan indicadores; las imágenes no se persisten. Si no se detecta
   persona, el resultado lo indica y no inventa postura ni contacto visual.
7. **Progreso y recomendaciones:** consultar historial y comparación; la siguiente
   práctica depende de resultados reales y reglas publicadas, sin entrenamiento
   automático personalizado del modelo.
8. **Informe:** abrir el informe desde resultados y usar Imprimir/Guardar PDF.
   Progreso también dispone de impresión. En el diálogo del navegador elegir PDF.
9. **Planes:** FREE permite cinco prácticas y una simulación creadas por mes UTC;
   también cuentan las abandonadas. PREMIUM no aplica esos límites. El checkout de
   demostración permite contratarlo con tarjetas ficticias, sin cargos reales.
   Desde Mi suscripción se consultan comprobantes y se cancela la renovación:
   el periodo pagado se conserva hasta el vencimiento, sin borrar grabaciones.
   Un pago pendiente necesita confirmación del administrador de pruebas.
10. **Privacidad:** borrar los datos de una práctica desde sesiones o los de una
    conversación desde simulaciones. Se eliminan contenido, audio y resultados;
    quedan metadatos mínimos para no reiniciar artificialmente la cuota. El archivo
    físico se elimina mediante una cola persistente con reintentos cada 30 segundos.

La recuperación de acceso es local y administrada, sin correo automático. Solicitar
al administrador la verificación de identidad y un cambio temporal; cambiarlo al
entrar. La aplicación no sustituye una evaluación docente ni clínica.

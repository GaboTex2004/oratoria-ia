# Monitoreo en tiempo real con IA local

**Administración → Monitoreo** muestra el estado de la plataforma en vivo y ofrece un asistente que lo explica en
lenguaje natural con el modelo local de la plataforma (Qwen vía Ollama). Todo corre en el mismo servidor: no usa
servicios de IA en la nube, no necesita cuentas externas y lo puede usar cualquier administrador desde el navegador.

## Cómo se usa

**En toda la plataforma:** el administrador ve abajo a la derecha, en cualquier pantalla, un botón con el estado
(*En orden*, *N alertas* o *Sin conexión*, en verde, ámbar o rojo). Consulta el estado solo cada 15 segundos. Si aparece
una alerta nueva, se abre por sí mismo y muestra el problema con **los pasos a seguir** (por ejemplo, qué contenedor
revisar). **Ver panel completo** lleva a Administración → Monitoreo. Los pasos son fijos por tipo de alerta
(`frontend/src/features/admin/monitoringSteps.ts`), no los genera la IA.

**En el panel completo:**

1. Entrar como administrador y abrir **Administración → Monitoreo**.
2. El panel se actualiza solo cada 10 segundos (se pausa si la pestaña del navegador no está visible). Los botones
   **Pausar** y **Actualizar ahora** controlan la actualización.
3. Revisar el estado general (*Todo en orden*, *Requiere atención* o *Problema crítico*), las **alertas** y las cifras
   por grupo: servicios, análisis de voz, evaluaciones, uso, seguridad, privacidad, notificaciones y servidor.
4. Para un diagnóstico, escribir una pregunta opcional (por ejemplo «¿Por qué hay análisis trabados?») y tocar
   **Analizar estado**. En 15 a 30 segundos aparece el resumen, los hallazgos con las cifras en que se basan, la
   respuesta a la pregunta y qué revisar.

## Cómo funciona

```
Navegador (admin)            Backend (Spring Boot)                     ai-service (FastAPI)        Ollama
-----------------            ---------------------                     --------------------        ------
GET /api/admin/monitoring ─► MonitoringService: consultas SQL y
                             reglas fijas → métricas + alertas
                             (no interviene la IA)
POST .../analysis ─────────► toma un estado nuevo y envía SOLO
  {question?}                nombre→valor de las métricas y alertas ─► /api/v1/monitoring/analyze ─► qwen2.5:1.5b
                                                                        valida la respuesta ◄──────── JSON con esquema
                           ◄─ resumen, hallazgos, recomendaciones ◄──
```

- **Las cifras no las inventa la IA.** Las calcula el backend con consultas a la base y la salud de cada servicio. Las
  alertas salen de reglas fijas: servicio caído (crítico), análisis o evaluaciones trabados más de 15 minutos (3 o más:
  crítico), 3 o más fallidos en una hora, más de 10 en cola, 10 o más inicios de sesión fallidos en una hora, borrados
  de audio atrasados más de 30 minutos, más notificaciones fallidas que entregadas y memoria del backend al 85 % o más.
- **Controles sobre el modelo:**
  - El esquema JSON que se le pide a Ollama solo permite citar como evidencia nombres de métricas recibidas.
  - Los hallazgos sin evidencia válida se descartan.
  - El estado nunca queda por debajo de la alerta más grave calculada por las reglas.
  - Si el modelo minimiza las alertas (por ejemplo «todo bien» con una alerta activa), su resumen se reemplaza por uno
    armado con las alertas, y el panel lo indica.
  - Junto a cada hallazgo se muestran los valores reales de las métricas citadas, para comprobarlo a simple vista.
- **Privacidad:** el modelo recibe solo nombres de métricas, números, estados y los textos de las alertas; nunca
  correos, nombres, transcripciones ni audios.
- **Prioridad de las prácticas:** el análisis se ejecuta solo cuando el administrador lo pide y nunca hace esperar a
  un usuario. Si hay evaluaciones en cola o en curso, el backend no lo inicia. En el ai-service el modelo atiende de a
  una petición: las prácticas esperan su turno hasta `LLM_SLOT_WAIT_SECONDS` (90 s), mientras que el monitoreo no
  espera (si el modelo está ocupado lo avisa y no inventa nada) y tiene su propio límite de 120 s, menor que el del
  backend. La salida tiene un máximo de hallazgos y recomendaciones para no cortarse.
- **Bitácora:** cada análisis completado queda registrado; los intentos rechazados (modelo ocupado) no.
- **Si la base de datos no responde,** el panel muestra igual el estado de los servicios con la alerta crítica.

## Limitaciones

- El modelo por defecto (`qwen2.5:1.5b`) es pequeño para funcionar en equipos modestos. Su redacción es **orientativa**:
  puede mezclar conceptos (por ejemplo «en proceso» y «fallidos»). Las cifras y alertas del panel son la referencia.
  Con un modelo más grande (`OLLAMA_MODEL`) las explicaciones mejoran, a costa de memoria y tiempo.
- Las notificaciones entregadas y fallidas se cuentan desde el último arranque del backend.

## Pruebas

- Backend: `MonitoringIntegrationTests` (solo ADMIN, métricas reales y alertas con un análisis trabado simulado, el
  modelo recibe solo métricas y alertas, modelo ocupado sin respuesta inventada, preguntas largas rechazadas).
- Frontend: `tests/monitoring.test.ts` (cada alerta tiene sus pasos) y `e2e/monitor-widget.spec.ts` (el botón aparece en
  el inicio, se abre solo ante una alerta nueva, muestra los pasos y lleva al panel; API simulada, sin backend).
- ai-service: `tests/test_monitoring.py` (evidencia limitada a métricas recibidas, piso de estado por alertas, resumen
  reemplazado si contradice las alertas, pregunta respondida, clave interna, límites de entrada y modelo ocupado).
- Verificación manual (19-nov-2026, Windows, Ollama 0.40 con `qwen2.5:1.5b`): análisis real en 15 a 27 segundos desde
  el panel, con hallazgos y evidencia; con 11 alertas, 43 a 69 segundos; un intento de inyección en la pregunta
  quedó neutralizado (resumen armado con las alertas y respuesta descartada); sin errores en el navegador.

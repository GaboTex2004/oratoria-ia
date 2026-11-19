# Instrucciones para agentes — OratoriaAI

## Objetivo del proyecto

OratoriaAI es una plataforma para practicar y mejorar la comunicación oral. Mantén el flujo React → Spring Boot → PostgreSQL y el servicio FastAPI de inferencia local. La aplicación debe funcionar sin enviar audio, texto, imágenes ni inferencias a proveedores de IA en la nube.

Responde y documenta en español, salvo nombres de APIs, código y mensajes técnicos establecidos por las dependencias.

## Criterios de calidad

Evalúa cada cambio con estos siete criterios y no declares cumplimiento total sin evidencia:

1. **Correctitud:** respeta los contratos de API, reglas de negocio, propiedad de los datos, cuotas y estados. Añade o ajusta pruebas que demuestren el comportamiento esperado y los errores relevantes.
2. **Eficiencia:** evita trabajo duplicado, consultas o inferencias innecesarias y cargas sin límites. Considera memoria, CPU/GPU, tamaño y duración de medios, latencia y recuperación asíncrona. Mide antes de afirmar una mejora de rendimiento.
3. **Fiabilidad:** maneja fallos de base de datos, servicios locales, modelos y procesamiento asíncrono con errores explícitos y recuperación segura. No conviertas fallos en notas o respuestas inventadas. Ejecuta las pruebas afectadas y el recorrido integral cuando cambien varias capas.
4. **Facilidad de uso:** conserva flujos claros, mensajes comprensibles, controles accesibles y diseño adaptable. No solicites permisos de micrófono o cámara antes de que la persona inicie una función que los requiera. Mantén y amplía las pruebas de interfaz y tamaños de pantalla.
5. **Mantenibilidad:** realiza cambios pequeños, con responsabilidades separadas y nombres claros. No edites migraciones ya aplicadas: agrega una migración nueva. Actualiza documentación y evidencias afectadas, y corrige contradicciones entre README, manuales, arquitectura, configuración y código.
6. **Seguridad e integridad:** no afirmes que el sistema está “libre de vulnerabilidades”. No incluyas secretos en el código, logs, frontend ni repositorio. Conserva autenticación, autorización por rol y propiedad, validación y límites de cargas, privacidad de audio e imágenes y separación del servicio interno. Comprueba dependencias con las herramientas existentes; informa el alcance de cada análisis.
7. **Portabilidad:** conserva Docker Compose, wrappers y lockfiles. No dependas de rutas absolutas del equipo ni de instalaciones globales innecesarias. Documenta sistema operativo, recursos, GPU y dependencias locales requeridos. Distingue “funciona en los entornos probados” de “funciona en cualquier plataforma”.

## Productividad, innovación e IA local

- Prioriza que el usuario pueda practicar, recibir retroalimentación comprensible, revisar su progreso e informes y continuar con recomendaciones útiles.
- La innovación del proyecto incluye transcripción y evaluación de voz, simulaciones y análisis corporal con ejecución local.
- Mantén Whisper/faster-whisper, Qwen/Ollama y MediaPipe como inferencia local. No agregues proveedores remotos, claves comerciales ni fallback a la nube sin un requisito explícito del usuario.
- La cámara es opcional. Describe las métricas de voz, rúbricas y postura como orientativas mientras no exista validación humana/científica suficiente. No presentes fixtures sintéticas como evidencia de precisión con personas.
- No afirmes que se entrenó un modelo propio ni que la rúbrica está validada por docentes si no existe evidencia versionada que lo demuestre.
- No imprimas, publiques ni copies los valores de `.env`. Las plantillas `.env.example` pueden documentar nombres de variables, nunca secretos reales.

## Pruebas obligatorias

Antes de entregar un cambio, ejecuta las pruebas que correspondan y comunica los comandos, resultados y limitaciones reales. No inventes cobertura ni resultados. No uses la base de datos de producción ni datos personales.

### Verificaciones por componente

- Backend, Python, frontend, lint y build: `.\scripts\Test-Local.ps1` si el entorno local está instalado y configurado.
- Suite aislada en Docker (Java con PostgreSQL efímero, Python/Ruff, frontend test/lint/build y E2E sin inferencia): `.\scripts\Test-Docker.ps1`.

### Recorrido integral con IA local

Cuando el cambio afecte UI, API, autenticación, audio, evaluación, modelos, simulaciones, progreso, cámara, informes, eliminación de datos o integración entre servicios, ejecuta el recorrido completo con datos sintéticos:

```powershell
.\scripts\New-SyntheticAudio.ps1
.\scripts\Test-Docker.ps1 -Models
```

Ese comando debe preparar `E2E_MODELS=1` y el WAV sintético. El Playwright E2E `local-model-journey.spec.ts` recorre registro e inicio de sesión, grabación ficticia, transcripción y evaluación con modelos locales, resultados, PDF, progreso, dos turnos de simulación, cámara ficticia y borrado de audio/datos. El mismo comando ejecuta además las suites Java, Python, frontend, pagos, administración y responsive definidas en el proyecto. En Docker, reconstruye las imágenes de prueba y también los servicios actuales `backend`, `ai-service` y `frontend` antes de E2E, para evitar probar imágenes obsoletas. Playwright usa el proxy local `frontend/e2e/local-proxy.cjs` y el origen loopback `http://127.0.0.1:15173`: loopback permite las APIs de medios ficticios y cumple la lista CORS habitual, sin añadir el alias interno de Docker a los orígenes CORS generales. Requiere Docker operativo, los servicios/modelos locales listos, un WAV sintético y credenciales admin de desarrollo; nunca lo ejecutes contra producción.

Si no hay recursos, modelos, voz sintética o entorno disponible, no simules el resultado ni marques el recorrido como aprobado. Indica qué parte se ejecutó, qué parte se omitió y la causa. Una respuesta HTTP de salud no demuestra que la inferencia local funcione.

## Antes de finalizar

- Revisa `git diff` para evitar cambios ajenos, secretos, archivos generados, medios, modelos y datos personales.
- Comprueba `docker compose config --quiet` si modificaste Compose o variables de despliegue.
- Mantén `.tools`, `.env`, bases, modelos y grabaciones locales fuera del control de versiones.
- Resume qué cambió, qué pruebas se ejecutaron y qué quedó pendiente. Distingue pruebas ejecutadas ahora de resultados históricos documentados.

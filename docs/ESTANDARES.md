# Criterios de mantenimiento

- Preservar arquitectura React → Spring → FastAPI local; el navegador no recibe
  la clave interna. No incorporar inferencia cloud ni fallback silencioso.
- Java 21 y Maven wrapper; cambios de esquema mediante una migración nueva,
  sin editar versiones aplicadas. Autorización y propiedad se verifican en servidor.
- TypeScript, componentes por módulo, API compartida y estilos adaptables. Mantener
  colores/contenido existentes salvo requisitos funcionales aprobados. Fuentes y
  licencias se conservan localmente en `frontend/src/assets/fonts`.
- Python: Ruff, tipos y errores explícitos; separar reglas, proveedor e infraestructura.
  Reglas pedagógicas deben llevar versión y limitaciones. No inventar notas,
  evidencia, datasets, porcentajes de cobertura ni entrenamiento propio.
- Los contratos actuales se describen en API.md. No registrar contraseñas, JWT,
  transcripciones o imágenes. No publicar `.env`, modelos, audio ni datos personales.
- Ejecutar `scripts/Test-Local.ps1`; para integración real añadir pruebas de
  navegador/modelos según el plan. Fixtures sintéticas se identifican como tales;
  no equivalen a validación con estudiantes ni exactitud científica.
- Commits descriptivos con identidad real configurada y sin atribuciones automáticas.
  Conservar autores originales. Esta entrega no autoriza push ni despliegue.

Deuda identificada: mejorar gradualmente el formato del servicio de simulaciones
existente; validar rúbricas con docentes y datos consentidos; pruebas de postura
con personas; medir cobertura instrumental y rendimiento con concurrencia.

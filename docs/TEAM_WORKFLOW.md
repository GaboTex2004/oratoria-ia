# Flujo de trabajo del equipo

## Ramas y alcance

Crear una rama corta por feature, por ejemplo:

- `feature/auth`
- `feature/session-recording`
- `feature/voice-analysis`
- `feature/progress-dashboard`

Cada rama debe concentrarse en uno o dos módulos relacionados. Evitar que varias
personas modifiquen al mismo tiempo archivos centrales como `App.tsx`, `pom.xml` o
la configuración global; acordar primero cualquier cambio transversal.

## Pull requests

- Hacer PRs pequeños, ejecutables y fáciles de revisar.
- Explicar objetivo, decisiones, pruebas y cambios de base de datos.
- Ejecutar build/lint del área tocada antes de solicitar revisión.
- No mezclar refactors generales con una feature funcional.
- No versionar `target`, `dist`, `node_modules`, entornos virtuales ni secretos.

## Configuración y contratos

- Documentar variables necesarias en el `.env.example` correspondiente.
- No cambiar contratos REST compartidos sin coordinar frontend, backend y AI Service.
- Añadir migraciones versionadas cuando el equipo adopte Flyway; nunca borrar el
  volumen compartido como procedimiento normal de desarrollo.
- Mantener datos de pago e IA detrás de interfaces/servicios de sus dominios.

## División inicial sugerida

- Integrante A: `auth` + `user`.
- Integrante B: `training` + `session`.
- Integrante C: `analysis` + contrato de `ai-service`.
- Integrante D: features frontend `dashboard` + `progress`.

`subscription` es transversal: asignar una persona responsable de revisar los usos de
`FeatureAccessService` aunque otros integrantes agreguen nuevos entitlements.

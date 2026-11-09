# Interfaz OratoriaAI

React, TypeScript y Vite. Conserva el diseño existente y agrega pantallas funcionales
de perfil, planes, recomendaciones, cámara, administración e informes. Fuentes
locales con licencias incluidas; no requiere Google Fonts durante la ejecución.

Desde esta carpeta: `npm.cmd ci`, `npm.cmd run dev -- --host 127.0.0.1`.
`VITE_API_URL` apunta al backend, nunca al servicio interno de modelos. Los pasos
completos están en [README raíz](../README.md).

Validación: `npm.cmd test`, `npm.cmd run lint`, `npm.cmd run build`.
E2E con Edge: `npm.cmd run test:e2e`; configurar los servicios y variables opcionales
según [el plan](../docs/pruebas/PLAN_DE_PRUEBAS.md). Cámara y audio de las pruebas
integrales son sintéticos, no permisos para usar dispositivos humanos.

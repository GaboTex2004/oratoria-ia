# Plan de finalización

Rama: `isaias-gutierrez`. Base: `2784c2e`. Sin push automático.
Fecha de inicio: 2026-11-08 (America/La_Paz).

| Fase | Estado | Evidencia / siguiente paso |
|---|---|---|
| A Auditoría/protección | Completada localmente | Rama de respaldo, historial/autores preservados; auditoría inicial |
| B Arquitectura | Completada localmente | Misma arquitectura; inferencia local sin fallback cloud; contratos y diagramas |
| C Backend/BD | Completada localmente | Perfil, cuotas, administración, privacidad y Flyway V1–V3; 49 pruebas Java |
| D IA local | Integrada y ejecutada | Whisper, Qwen CPU/GPU, acústica y Pose; cinco comprobaciones por perfil; validación humana pendiente |
| E Frontend/integración | Implementada | Pantallas conectadas, PDF, fuentes locales y seis anchos verificados; recorrido integral documentado en informe |
| F Usuarios/datos | Completada en dev | ADMIN/FREE/PREMIUM por entorno; seed idempotente, 20 ejercicios y ocho escenarios |
| G Pruebas | Ver informe final | 49 Java, 50 Python y 14 frontend; navegador/modelos con evidencia y fallos históricos explícitos |
| H Documentación/entrega | Documentada localmente | API, BD, manuales, modelos, 24 HU propuestas, resultados y pendientes; sin publicación automática |

Cierre de integración: ocho pruebas de navegador aprobadas en 2.6 minutos tras
corregir el cliente corporal; HTTP 200 real en Pose, PDF individual/progreso
generados y borrado de contenido verificado. Código de Python: formato comprobado
y 50 pruebas repetidas satisfactoriamente después del ajuste de formato final.

## Condiciones que requieren evidencia adicional

- Documento de Software I no localizado: el usuario autorizó 24 HU nuevas para Scrum.
  REQUISITOS_SCRUM.md las identifica como propuesta nueva, sin atribuirlas al original.
- Cámara/micrófono: solo por acción explícita del usuario en el navegador.
- No hay dataset etiquetado de oratoria: no afirmar entrenamiento supervisado propio.
- No realizar cobros ni publicar cambios.

## Registro de cambios

- Auditoría y respaldo local sin alterar historial.
- Ver informe de pruebas para comandos, resultados y limitaciones actualizados.
- Correcciones de integración: limpiar clases Java obsoletas; importar fuentes desde
  main.tsx; acotar caja de iconos en móvil; selector accesible de práctica; forzar
  HTTP/1.1 también en el cliente corporal, igual que los clientes existentes.
- Dependencias frontend actualizadas sin cambio mayor de framework: npm audit pasó
  de 13 avisos a cero. El lockfile conserva versiones exactas.
- El inventario de archivos modificados acompaña a la evidencia. La base remota no
  contiene esta finalización mientras no se autorice y realice push.

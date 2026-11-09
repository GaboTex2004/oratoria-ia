# Evidencia de pagos de demostración

Ejecutada el 2026-11-08 en America/La_Paz. Las cuentas, tarjetas, autorizaciones y
operaciones son sintéticas. No se hicieron cargos reales ni solicitudes bancarias.

- `backend.txt`: resumen real de Maven, 69 pruebas sin fallos/errores/omitidas.
- `navegador-regresion.txt`: 12 pruebas Edge, incluidos modelos locales y pagos.
- `navegador-pagos.txt`: repetición final de las cuatro pruebas de pagos, con
  aviso persistente al desplazarse y generación PDF del comprobante.
- `approved-receipt.png`, `declined-receipt.png`, `pending-receipt.png`: capturas
  reales de los tres resultados, con cuenta ficticia y referencia de orden.
- `mobile-plans-320.png`: captura real de planes a 320 px.
- `synthetic-receipt.pdf`: comprobante PDF generado por Chromium, sin valor fiscal.

No se incluyen credenciales, capturas administrativas con usuarios locales ni el
backup SQL privado. Los logs completos permanecen en `.tools`, excluidos de Git.

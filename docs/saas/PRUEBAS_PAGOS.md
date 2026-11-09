# Pruebas del simulador de pagos

Fecha: 2026-11-08, America/La_Paz. Fixtures sintéticas, sin información financiera
ni pagos reales. No se eliminaron pruebas existentes. La suite usa oratoria_test,
separada de oratoria_db; los recorridos de navegador crean cuentas sintéticas dev.

## Ejecución backend

Desde backend, con PostgreSQL de pruebas preparado y Java 21:

```powershell
.\mvnw.cmd -B test
```

Última ejecución completa: **69 pruebas, 0 fallos, 0 errores, 0 omitidas**.
49 previas y 20 nuevas: PaymentIntegrationTests (18) y
ProductionPaymentIntegrationTests (2). Registro local `.tools/payments-java-verified.log`.
No se infiere un porcentaje de cobertura a partir de estas cantidades.

| Caso exigido | Evidencia automatizada |
|---|---|
| Usuario FREE | Plan efectivo y cuota de cinco prácticas |
| Selección Premium / orden | Importe del plan capturado, BOB y PENDING_PAYMENT |
| Aprobada | UUID/autorización ficticia, fechas y permisos Premium reales |
| Rechazada | FREE, sin autorización y posibilidad de nueva orden |
| Pendiente | FREE hasta confirmación ADMIN |
| Confirmar pendiente | Aprobación/rechazo ADMIN y eventos persistidos |
| Doble envío | Misma clave devuelve orden; misma orden devuelve transacción |
| Doble activación | No extiende fechas ni genera segunda activación |
| Concurrencia | Dos hilos reales procesan una orden: una transacción y una suscripción activa |
| Cancelación/vencimiento | Conserva periodo, luego FREE; sesiones conservadas |
| Renovación | Mes de calendario; aprobada, rechazada y pendiente |
| Gracia/downgrade | Mantiene temporalmente, después FREE y pierde acceso Premium |
| No autorizado | 401 sin login, 403 USER en controles ADMIN |
| Datos ajenos | 404 al leer/procesar orden o consultar transacción ajena |
| Protección producción | Contexto production + dev + flag true rechaza checkout con 403 |
| Flag/perfil | Sin perfil permitido o con flag false no se habilita |
| Manipulación | Estado APPROVED sin scenario no aprueba; moneda/importe inválidos rechazados |
| Idempotency-Key ausente | 400, sin crear orden |

V4 se aplicó satisfactoriamente en la base de pruebas y en la base dev respaldada.
No se editaron V1–V3. JdbcTemplate y JPA comparten transacción; no se confirma el
plan si falla el registro de pago. UNIQUE y bloqueo pesimista se probaron con dos
peticiones de servicio simultáneas sobre datos confirmados en la base aislada.

## Frontend y recorrido extremo a extremo

ESLint, TypeScript/Vite y las **14 pruebas unitarias existentes** aprobaron.
Nuevas pruebas Edge en `frontend/e2e/payments.spec.ts`:

1. Registro por interfaz → FREE → Premium aprobado → seis prácticas (supera cuota
   FREE) → historial → cancelar → avanzar 40 días como ADMIN → FREE; conserva seis
   prácticas y rechaza una séptima al volver al límite FREE.
2. Tarjeta rechazada → comprobante sin autorización → FREE.
3. Tarjeta pendiente → USER no puede confirmar → ADMIN confirma en su panel real
   desde otro contexto de navegador → usuario actualiza y obtiene Premium.
4. Checkout, suscripción y planes a 320 px, sin desborde ni campos de tarjeta real.

La suite completa de navegador aprobó **12 pruebas en 2.6 minutos**, incluida la
regresión anterior y los modelos locales con audio/cámara sintéticos. Tras mantener
el aviso de demostración visible al desplazarse se repitieron las cuatro pruebas
de pagos: aprobadas en 8.5 segundos. La repetición final verifica además el PDF
real del comprobante: **cuatro aprobadas en 9.1 segundos** con el backend final.
Resultados y capturas están en `evidencias`.

Para repetir: iniciar servicios según README; desde frontend `npm.cmd run test:e2e`.
Para incluir ADMIN proporcionar E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD por entorno;
para modelos, E2E_MODELS=1 y E2E_SYNTHETIC_AUDIO con ruta absoluta al WAV sintético.
Nunca incluir esos valores en el repositorio ni usar cuentas de producción.

## Fallos encontrados

La primera compilación encontró ambigüedad en referencias a métodos sobrecargados
del mapeador JDBC; se renombraron mapOrder/mapTransaction y recompiló. Primera
corrida de pagos: tres aprobadas y una fallida por selector H1 ambiguo (saludo y
título de página); se acotó al contenido de la pantalla y la repetición aprobó.

No se probó una red bancaria porque no existe integración bancaria. No se certifica
PCI, procesamiento monetario, facturación fiscal ni resistencia bajo carga masiva.
La prueba concurrente verifica dos solicitudes, no una prueba de carga.

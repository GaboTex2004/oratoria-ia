# Pasarela académica de pagos — OratoriaAI

**MODO DEMOSTRACIÓN — No se realizan cargos reales.** No existe integración bancaria,
red de tarjetas ni proveedor comercial. No ingresar tarjetas reales. Solo se envían
identificadores de escenarios; no existen campos de PAN completo, CVV o vencimiento.

## Activación y aislamiento

Se necesitan simultáneamente `BILLING_DEMO_ENABLED=true` y un perfil Spring
`dev`, `test` o `demo`. `prod` o `production` deshabilitan el simulador incluso si
también aparece dev y se intenta habilitar el flag. Sin perfil permitido está
deshabilitado por defecto. La configuración vive en el backend, no en React.

`scripts/Initialize-Local.ps1` prepara el flag para la instalación local dev y
conserva valores existentes. Reiniciar el backend después de configurarlo. No
copiar esa configuración a producción. Las rutas de lectura siguen autenticadas;
crear/procesar/cancelar o confirmar/avanzar rechaza operación si no está habilitado.
El proveedor simulado también verifica el guard, no solo el controlador.

| Configuración backend | Valor predeterminado |
|---|---|
| BILLING_DEMO_ENABLED | false |
| BILLING_PREMIUM_PRICE | 49.00 |
| BILLING_GRACE_DAYS | 3, válido de 1 a 14 |

FREE cuesta Bs 0. PREMIUM mensual usa el precio de configuración persistido en
`plans.price`, NUMERIC(12,2), y moneda BOB. No hay un precio 49 incrustado en React.
Cada orden captura el importe del servidor; un cambio posterior del precio no
modifica una orden existente. El proveedor valida estado PENDING, importe positivo
y moneda BOB. Todos los cálculos monetarios Java usan BigDecimal.

## Escenarios deterministas

| Identificador enviado | Tarjeta visual ficticia | Resultado backend |
|---|---|---|
| DEMO_APPROVED | Visa ···· 4242 | APPROVED, activa Premium |
| DEMO_DECLINED | Visa ···· 0002 | REJECTED, conserva FREE |
| DEMO_PENDING | Visa ···· 0003 | PENDING, requiere ADMIN para confirmar/rechazar |

Estos sufijos son ilustraciones, no se envían a redes de tarjetas. El navegador
envía únicamente `{ "scenario": "DEMO_APPROVED" }` a la referencia de orden en la
URL. No acepta importe, autorización o estado de aprobación del cliente como fuente
de verdad. El comprobante es de demostración y carece de valor fiscal/bancario.

## Persistencia y reutilización

V4 es aditiva; V1–V3 permanecen intactas. Se reutilizan las entidades JPA Plan y
Subscription. PlanFeature y PlanLimit ya están representados por **PlanEntitlement**,
con discriminadores feature/usage_limit: no se duplicaron tablas de permisos.
PaymentOrder y PaymentTransaction son modelos tipados en BillingStore y se guardan
mediante JdbcTemplate dentro de la misma transacción Spring/JPA. PaymentEvent,
AuditLog y UsageRecord se persisten en `payment_events`, `billing_audit_logs` y
`usage_records`. No se necesita una entidad JPA paralela para cada tabla.

El consumo autoritativo continúa contando sesiones/simulaciones creadas en sus
tablas originales, incluidas las anteriores a V4. `usage_records` registra nuevas
reservas de creación dentro de la misma transacción y revierte si falla la creación.
No se recalculan datos históricos inventados ni se duplica su consumo.

## Administración de pruebas

Entrar como ADMIN y abrir `/admin/payments` desde administración general o planes.
El panel permite ver operaciones globales, confirmar/rechazar pagos pendientes y,
solo en dev/test, avanzar de 1 a 400 días el reloj de facturación de un usuario.
No acepta fechas arbitrarias, no cambia el reloj Windows ni el reloj de otros
usuarios. Todos estos cambios quedan auditados. Un usuario USER recibe 403 aun
conociendo las rutas administrativas.

La asignación manual previa de planes ADMIN se conserva como concesión administrativa
sin renovación simulada. El seed PREMIUM previo es una concesión de demostración
sin vencimiento, no un pago ficticio ya aprobado; para demostrar checkout registrar
una cuenta FREE nueva. La simulación no genera retroactivamente pagos para seeds.

## Seguridad y operación

JWT/rol/propietario se verifican en servidor. Otra persona recibe 404 al consultar
una orden/transacción ajena. Cada usuario tiene un bloqueo pesimista durante cambios
de suscripción; UNIQUE protege clave de orden y transacción por orden. Los eventos
se generan dentro de la transacción de negocio; rollback evita Premium sin pago.
No hay endpoint público de aprobación ni webhook externo. Los comprobantes están
autenticados. Historial paginado en bloques de 50. No se publicaron credenciales.

Antes de V4 se guardó un backup privado en `.tools/oratoria-pre-payments.dump`.
El volumen PostgreSQL se conservó. No borrar tablas o editar migraciones aplicadas.
Ver [flujo y API](FLUJO_CHECKOUT.md), [estados](ESTADOS_SUSCRIPCION.md) y
[pruebas reales](PRUEBAS_PAGOS.md).

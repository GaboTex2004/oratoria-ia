# Flujo de checkout y contratos

**Simulación académica sin cargos reales.** Arquitectura: React → Spring Boot →
PostgreSQL. PaymentProvider tiene una sola implementación MockPaymentProvider y
no realiza llamadas de red. El motor de IA es independiente de este módulo.

1. Registrar/iniciar sesión: la cuenta recibe FREE.
2. `/plans` consulta precio y cuotas del servidor; seleccionar Premium crea orden.
3. Se crea Subscription PENDING_PAYMENT y PaymentOrder PENDING. FREE sigue activa.
4. Checkout muestra precio mensual BOB y tres tarjetas ficticias.
5. Spring selecciona resultado según scenario y genera UUID de transacción.
6. APPROVED registra autorización ficticia, expira FREE, activa la suscripción
   pendiente y establece inicio/vencimiento mensual. Todo se confirma atómicamente.
7. REJECTED marca contratación rechazada y conserva FREE. Se permite nueva orden.
8. PENDING conserva FREE y espera confirmación ADMIN en entorno habilitado.
9. React actualiza identidad/plan solo después de la respuesta backend y muestra
   el comprobante real persistido. Recargar el comprobante no vuelve a cobrar.

## API autenticada

Base `/api`. JWT Bearer obligatorio. UUID en referencias. Respuestas exitosas 200;
validación/conflicto de estado 400, falta de login 401, permisos/entorno 403 y
recurso inexistente o ajeno 404. No enviar atributos monetarios ni credenciales.

| Método / ruta | Entrada | Salida |
|---|---|---|
| GET /plans | Ninguna | Plan[]: code,name,price,currency,interval,entitlements |
| GET /subscriptions/current | Ninguna | SubscriptionView: plan efectivo, estado, fechas, autoRenew, pendingStatus, effectiveNow, simulatorEnabled |
| POST /payments/orders | Header Idempotency-Key; sin body | Receipt con orden y transaction null |
| GET /payments/orders/{id} | Referencia propia | Receipt persistido |
| POST /payments/orders/{id}/process | `{ "scenario": "DEMO_APPROVED" }` | Receipt con transacción APPROVED/REJECTED/PENDING |
| GET /payments/transactions/{id} | Referencia propia | Receipt de su orden |
| GET /payments/history?page=0 | Página 0–10000 | Receipt[] propios, hasta 50, más recientes primero |
| POST /subscriptions/current/cancel-renewal | Sin body | SubscriptionView actualizado |
| GET /subscriptions/current/usage | Ninguna | practices/simulations: used,limit,unlimited; periodStart y zonas explícitas |
| GET /admin/payments?page=0 | ADMIN | Receipt[] globales, hasta 50 |
| POST /admin/payments/orders/{id}/confirm | ADMIN: `{ "decision": "APPROVE" }` o REJECT | Receipt; requiere transacción y orden aún PENDING |
| POST /admin/payments/users/{id}/advance | ADMIN dev/test: `{ "days": 32, "renewalScenario": "DEMO_DECLINED" }` | SubscriptionView; avanza reloj y concilia vencimientos |

La clave de creación contiene 8–100 caracteres A–Z/a–z/0–9/guion/guion bajo.
Repetirla devuelve la misma orden, aunque su estado haya cambiado. No crea una
segunda suscripción. Otro checkout pendiente bloquea nuevas órdenes con otra clave.
El procesamiento es idempotente por referencia de orden: UNIQUE(order_id) y bloqueo
de usuario. Repetir el mismo escenario devuelve la transacción original sin extender
fechas. Intentar cambiar de escenario en una orden procesada devuelve 400.

Receipt: `demonstration`, `notice`, `order`, `transaction` y `events`. order contiene
id, userId, subscriptionId, amount, currency, status, kind y createdAt; transaction
contiene UUID propio, orderId, scenario, status, authorizationCode ficticio y fechas.
Sin aprobación, authorizationCode es null. El comprobante no es una factura.

## Navegación

- `/plans`: comparación FREE/PREMIUM y creación de checkout.
- `/checkout/:id`: selección de tarjeta, estado y comprobante imprimible/PDF.
- `/subscription`: plan, fechas, consumo mensual, cancelación e historial paginado.
- `/admin/payments`: operaciones globales, confirmación y reloj de pruebas.

El backend puede rechazar una operación aunque el botón sea visible por datos
desactualizados. La interfaz muestra el error y no finge éxito. El banner de
demostración permanece en todas estas páginas. Para continuar una orden sin pagar,
abrir Mi suscripción → Historial → Continuar checkout.

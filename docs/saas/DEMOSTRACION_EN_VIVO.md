# Guion breve para la defensa

**Simulación académica; no hay cargos reales, tarjetas reales ni integración bancaria.**
Abrir http://localhost:5173 con los servicios del README iniciados. Usar dos
sesiones de navegador (normal/privada) para no mezclar usuario y ADMIN.

1. Registrar una cuenta nueva: Mi suscripción muestra FREE y consumo real.
2. Planes → Premium: precio configurable del servidor, por defecto Bs 49/mes.
3. Elegir Visa ficticia ···· 4242 → Confirmar pago simulado. Mostrar el comprobante,
   UUID de transacción y autorización DEMO; imprimir/guardar PDF si se desea.
4. Mi suscripción muestra Premium, inicio, vencimiento y renovación simulada.
   Crear más de cinco prácticas demuestra su cuota ilimitada. No hace falta grabar
   voz ni usar hardware para demostrar ese permiso.
5. Revisar historial; cancelar renovación. Premium sigue hasta el vencimiento.
6. En la segunda sesión, entrar como ADMIN → Administración → Pagos simulados.
   Elegir la cuenta y avanzar 40 días. Volver al usuario y actualizar suscripción:
   FREE, con todas sus sesiones anteriores conservadas.
7. Con otra cuenta FREE, probar Visa ···· 0002: rechazo y sin Premium.
8. Con otra cuenta FREE, probar Visa ···· 0003: pendiente y sin Premium. ADMIN
   confirma la orden correspondiente; actualizar estado en el checkout del usuario
   y verificar Premium.
9. Para renovación aprobada usar otra Premium no cancelada y avanzar 32 días con
   resultado Aprobada. Para gracia, elegir Rechazada y avanzar hasta un día después
   de su vencimiento (el mes tiene duración variable), luego cuatro días más para
   demostrar la bajada a FREE. Un periodo de gracia no es un pago aprobado.

La cuenta PREMIUM del seed es una concesión administrativa anterior, sin checkout
ni renovación. Para esta demostración usar nuevas cuentas registradas FREE.
El reloj avanzado pertenece a cada cuenta de prueba y no cambia Windows ni el
mes de consumo UTC. No usar cuentas de otras personas para la demostración.

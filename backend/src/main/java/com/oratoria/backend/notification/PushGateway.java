package com.oratoria.backend.notification;

/** Entrega un mensaje ya serializado a una suscripción. Se reemplaza en pruebas para no salir a internet. */
public interface PushGateway {
    /**
     * DELIVERED: aceptado. GONE: el navegador anuló la suscripción (404/410), se borra.
     * FAILED: fallo de red o del servicio, cuenta para el límite de reintentos.
     * REJECTED: el servicio rechazó al servidor (401/403/413/429: VAPID, reloj, tamaño o cuota); no es culpa de la
     * suscripción, así que no se borra ni suma fallos.
     */
    enum Delivery { DELIVERED, GONE, FAILED, REJECTED }

    record Target(String endpoint, String p256dh, String authSecret) {}

    Delivery send(Target target, byte[] payload);
}

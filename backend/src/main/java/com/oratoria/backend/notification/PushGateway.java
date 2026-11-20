package com.oratoria.backend.notification;

/** Entrega un mensaje ya serializado a una suscripción. Se reemplaza en pruebas para no salir a internet. */
public interface PushGateway {
    enum Delivery { DELIVERED, GONE, FAILED }

    record Target(String endpoint, String p256dh, String authSecret) {}

    Delivery send(Target target, byte[] payload);
}

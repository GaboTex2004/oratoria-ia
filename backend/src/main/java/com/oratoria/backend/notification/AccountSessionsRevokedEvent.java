package com.oratoria.backend.notification;

import java.util.UUID;

/** Se publica cuando se invalidan todas las sesiones de una cuenta (cambio o restablecimiento de contraseña). */
public record AccountSessionsRevokedEvent(UUID userId) {}

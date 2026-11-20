package com.oratoria.backend.notification;

import java.util.UUID;

/** Se publica cuando la evaluación de una práctica quedó guardada como completada. */
public record PracticeEvaluatedEvent(UUID sessionId) {}

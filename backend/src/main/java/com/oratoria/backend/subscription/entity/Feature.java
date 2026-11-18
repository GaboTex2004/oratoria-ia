package com.oratoria.backend.subscription.entity;

public enum Feature {
    BASIC_VOICE_ANALYSIS("Análisis de voz básico"),
    ADVANCED_VOICE_ANALYSIS("Análisis de voz avanzado"),
    CAMERA_ANALYSIS("Análisis con cámara"),
    ADVANCED_STATISTICS("Estadísticas avanzadas"),
    FULL_HISTORY("Historial completo"),
    SESSION_COMPARISON("Comparar sesiones"),
    ADAPTIVE_TRAINING("Entrenamiento adaptativo"),
    IMPROVISATION("Improvisación"),
    COMMUNICATION_STYLES("Estilos de comunicación"),
    UNLIMITED_SIMULATIONS("Simulaciones ilimitadas");

    private final String label;

    Feature(String label) { this.label = label; }

    public String label() { return label; }
}

package com.oratoria.backend.admin.audit;

public enum AuditAction {
    LOGIN_SUCCESS("Inicio de sesión"),
    LOGIN_FAILED("Inicio de sesión fallido"),
    REGISTER("Registro de cuenta"),
    PASSWORD_CHANGED("Cambio de contraseña"),
    USER_UPDATED("Usuario actualizado"),
    EXERCISE_CREATED("Ejercicio creado"),
    EXERCISE_UPDATED("Ejercicio actualizado"),
    CATEGORY_CREATED("Categoría creada"),
    CATEGORY_UPDATED("Categoría actualizada"),
    PLAN_UPDATED("Plan actualizado"),
    SETTINGS_UPDATED("Parámetros del sistema actualizados"),
    PAYMENT_CONFIRMED("Pago de prueba confirmado"),
    BILLING_CLOCK_ADVANCED("Reloj de facturación adelantado"),
    BACKUP_CREATED("Respaldo generado"),
    BACKUP_DELETED("Respaldo eliminado"),
    BACKUP_RESTORED("Respaldo restaurado"),
    ADMIN_ACTION("Otra acción administrativa");

    private final String label;

    AuditAction(String label) { this.label = label; }

    public String label() { return label; }
}

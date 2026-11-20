package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oratoria.backend.admin.health.SystemHealthService;
import com.oratoria.backend.admin.health.SystemHealthService.ModuleStatus;
import com.oratoria.backend.admin.monitoring.MonitoringService;
import com.oratoria.backend.notification.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonitoringServiceTests {
    @Test void withTheDatabaseDownThePanelStillShowsTheServicesAndACriticalAlert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SystemHealthService health = mock(SystemHealthService.class);
        NotificationService notifications = mock(NotificationService.class);
        when(health.check()).thenReturn(List.of(
                new ModuleStatus("database", "DOWN", 30000, "No responde o no está disponible."),
                new ModuleStatus("ai-service", "UP", 20, "Servicio de IA disponible.")));

        MonitoringService.Snapshot snapshot = new MonitoringService(jdbc, health, notifications).snapshot();

        assertThat(snapshot.status()).isEqualTo(MonitoringService.Severity.CRITICO);
        assertThat(snapshot.alerts()).extracting(MonitoringService.Alert::code).contains("SERVICIO_BASE_DATOS_DOWN");
        assertThat(snapshot.values()).containsEntry("servicio_base_datos", "DOWN").containsKey("memoria_backend_pct");
        assertThat(snapshot.events()).isEmpty();
        verifyNoInteractions(jdbc, notifications);
    }
}

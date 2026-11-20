package com.oratoria.backend.notification;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Envía los avisos fuera del hilo de evaluación: una red lenta no debe retrasar el procesamiento de prácticas. */
@Component
public class NotificationEvents {
    private static final Logger log = LoggerFactory.getLogger(NotificationEvents.class);
    private final NotificationService notifications;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "oratoria-push-sender");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationEvents(NotificationService notifications) { this.notifications = notifications; }

    @EventListener
    public void onPracticeEvaluated(PracticeEvaluatedEvent event) {
        sender.execute(() -> {
            try { notifications.notifyPracticeReady(event.sessionId()); }
            catch (RuntimeException failure) { log.warn("No se pudo enviar el aviso de resultado [sessionId={}]", event.sessionId()); }
        });
    }

    @PreDestroy
    void shutdown() { sender.shutdown(); }
}

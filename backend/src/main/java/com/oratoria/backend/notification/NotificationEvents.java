package com.oratoria.backend.notification;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cola de envíos push. Los envíos salen de hilos propios: un servicio push lento no debe frenar las evaluaciones,
 * los demás trabajos programados ni las peticiones del administrador.
 */
@Component
public class NotificationEvents {
    private static final Logger log = LoggerFactory.getLogger(NotificationEvents.class);
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final AtomicInteger threadIndex = new AtomicInteger(1);
    private final ExecutorService sender = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10_000), runnable -> {
        Thread thread = new Thread(runnable, "oratoria-push-sender-" + threadIndex.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    public NotificationEvents(NotificationService notifications, JdbcTemplate jdbc) {
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    /** Encola un trabajo de envío; si la app se está apagando o la cola está llena, se descarta con un aviso en el log. */
    public void dispatch(String description, Runnable job) {
        try {
            sender.execute(() -> {
                try { job.run(); }
                catch (RuntimeException failure) { log.warn("Falló un envío push [{}]", description); }
            });
        } catch (RejectedExecutionException rejected) {
            log.warn("Envío push descartado [{}]", description);
        }
    }

    @EventListener
    public void onPracticeEvaluated(PracticeEvaluatedEvent event) {
        dispatch("resultado " + event.sessionId(), () -> notifications.notifyPracticeReady(event.sessionId()));
    }

    /** Si cambió la contraseña, ningún dispositivo debe seguir recibiendo los avisos de esa cuenta. */
    @EventListener
    public void onSessionsRevoked(AccountSessionsRevokedEvent event) {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", event.userId());
    }

    @PreDestroy
    void shutdown() { sender.shutdown(); }
}

package com.oratoria.backend.notification;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso del administrador a todos los dispositivos suscritos (queda en la bitácora por estar bajo /api/admin).
 * Responde 202 de inmediato: el envío sigue en segundo plano y no depende del tiempo de respuesta de los servicios push.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {
    private final NotificationService service;
    private final NotificationEvents queue;

    public AdminNotificationController(NotificationService service, NotificationEvents queue) {
        this.service = service;
        this.queue = queue;
    }

    public record BroadcastRequest(String title, String body) {}

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Integer> broadcast(@RequestBody BroadcastRequest request) {
        NotificationService.Broadcast broadcast = service.prepareBroadcast(request.title(), request.body());
        for (var userId : broadcast.recipients()) queue.dispatch("aviso masivo", () -> service.sendToUser(userId, broadcast.message()));
        return Map.of("devices", broadcast.devices(), "recipients", broadcast.recipients().size());
    }
}

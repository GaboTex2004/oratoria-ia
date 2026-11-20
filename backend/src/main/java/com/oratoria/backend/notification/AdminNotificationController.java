package com.oratoria.backend.notification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aviso del administrador a todos los dispositivos suscritos (queda en la bitácora por estar bajo /api/admin). */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {
    private final NotificationService service;

    public AdminNotificationController(NotificationService service) { this.service = service; }

    public record BroadcastRequest(String title, String body) {}

    @PostMapping("/broadcast")
    NotificationService.SendSummary broadcast(@RequestBody BroadcastRequest request) {
        return service.broadcast(request.title(), request.body());
    }
}

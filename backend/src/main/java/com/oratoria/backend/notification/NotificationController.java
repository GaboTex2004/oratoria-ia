package com.oratoria.backend.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}

    public record SubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {}

    public record EndpointRequest(@NotBlank @Size(max = 1000) String endpoint) {}

    /** Clave pública VAPID que el navegador necesita para suscribirse. */
    @GetMapping("/config")
    Map<String, Object> config() { return Map.of("publicKey", service.publicKey()); }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(Principal principal, @Valid @RequestBody SubscriptionRequest request,
                   @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        service.subscribe(principal.getName(), request.endpoint().trim(), request.keys().p256dh(), request.keys().auth(), userAgent);
    }

    @GetMapping("/subscriptions/current")
    Map<String, Boolean> current(Principal principal, @RequestParam String endpoint) {
        return Map.of("registered", service.isRegistered(principal.getName(), endpoint.trim()));
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(Principal principal, @Valid @RequestBody EndpointRequest request) {
        service.unsubscribe(principal.getName(), request.endpoint().trim());
    }

    @GetMapping("/settings")
    NotificationService.SettingsView settings(Principal principal) { return service.settings(principal.getName()); }

    @PutMapping("/settings")
    NotificationService.SettingsView updateSettings(Principal principal, @RequestBody NotificationService.SettingsUpdate request) {
        return service.updateSettings(principal.getName(), request);
    }

    @PostMapping("/test")
    NotificationService.SendSummary test(Principal principal) { return service.sendTest(principal.getName()); }
}

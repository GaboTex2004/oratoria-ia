package com.oratoria.backend.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Envía el mensaje cifrado al servicio push del navegador (Google, Mozilla, Microsoft o Apple). */
@Component
public class HttpPushGateway implements PushGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpPushGateway.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final VapidKeyService vapid;
    private final String subject;

    public HttpPushGateway(VapidKeyService vapid, @Value("${app.push.subject}") String subject) {
        this.vapid = vapid;
        this.subject = PushSubject.validate(subject);
    }

    @Override
    public Delivery send(Target target, byte[] payload) {
        String host = URI.create(target.endpoint()).getHost();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", WebPushCrypto.vapidAuthorization(target.endpoint(), vapid.keys(), subject, Instant.now()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(WebPushCrypto.encrypt(payload, target.p256dh(), target.authSecret())))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) return Delivery.DELIVERED;
            if (status == 404 || status == 410) return Delivery.GONE;
            String reason = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
            log.warn("El servicio push respondió {} [host={} motivo={}]", status, host, reason.substring(0, Math.min(reason.length(), 200)));
            return status == 401 || status == 403 || status == 413 || status == 429 ? Delivery.REJECTED : Delivery.FAILED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Delivery.FAILED;
        } catch (Exception failure) {
            log.warn("No se pudo entregar una notificación push [host={} error={}]", host, failure.getClass().getSimpleName());
            return Delivery.FAILED;
        }
    }
}

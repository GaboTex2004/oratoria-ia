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
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final VapidKeyService vapid;
    private final String subject;

    public HttpPushGateway(VapidKeyService vapid, @Value("${app.push.subject:mailto:soporte@oratoria.local}") String subject) {
        this.vapid = vapid;
        this.subject = subject;
    }

    @Override
    public Delivery send(Target target, byte[] payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target.endpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", WebPushCrypto.vapidAuthorization(target.endpoint(), vapid.keys(), subject, Instant.now()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(WebPushCrypto.encrypt(payload, target.p256dh(), target.authSecret())))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) return Delivery.DELIVERED;
            // 404/410: el navegador anuló la suscripción; 403 suele indicar claves VAPID distintas a las de la suscripción.
            if (status == 404 || status == 410 || status == 403) return Delivery.GONE;
            log.warn("El servicio push respondió {} [host={}]", status, URI.create(target.endpoint()).getHost());
            return Delivery.FAILED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Delivery.FAILED;
        } catch (Exception failure) {
            log.warn("No se pudo entregar una notificación push [host={} error={}]",
                    URI.create(target.endpoint()).getHost(), failure.getClass().getSimpleName());
            return Delivery.FAILED;
        }
    }
}

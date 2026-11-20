package com.oratoria.backend.notification;

import com.oratoria.backend.notification.WebPushCrypto.VapidKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Claves VAPID del servidor. Si llegan por variables de entorno se usan esas; si no, se generan una vez y se
 * guardan en la base para que sobrevivan reinicios (cambiarlas invalida todas las suscripciones).
 * La tabla no entra en los respaldos: la clave privada nunca sale del servidor.
 */
@Service
public class VapidKeyService {
    private final JdbcTemplate jdbc;
    private final String configuredPublic;
    private final String configuredPrivate;
    private volatile VapidKeys cached;

    public VapidKeyService(JdbcTemplate jdbc,
                           @Value("${app.push.vapid-public-key:}") String configuredPublic,
                           @Value("${app.push.vapid-private-key:}") String configuredPrivate) {
        this.jdbc = jdbc;
        this.configuredPublic = configuredPublic.trim();
        this.configuredPrivate = configuredPrivate.trim();
        if (this.configuredPublic.isEmpty() != this.configuredPrivate.isEmpty()) {
            throw new IllegalStateException("Define ambas claves VAPID (PUSH_VAPID_PUBLIC_KEY y PUSH_VAPID_PRIVATE_KEY) o ninguna.");
        }
        // Falla al arrancar, y no en cada envío, si el par configurado no es válido o no corresponde entre sí.
        if (!this.configuredPublic.isEmpty()) WebPushCrypto.normalizeVapidKeys(this.configuredPublic, this.configuredPrivate);
    }

    public VapidKeys keys() {
        VapidKeys current = cached;
        if (current != null) return current;
        synchronized (this) {
            if (cached == null) cached = load();
            return cached;
        }
    }

    private VapidKeys load() {
        if (!configuredPublic.isEmpty()) return WebPushCrypto.normalizeVapidKeys(configuredPublic, configuredPrivate);
        var stored = jdbc.query("SELECT public_key, private_key FROM push_vapid_keys WHERE id = 1",
                (rs, row) -> new VapidKeys(rs.getString(1), rs.getString(2)));
        if (!stored.isEmpty()) return stored.getFirst();
        VapidKeys generated = WebPushCrypto.generateVapidKeys();
        // Si otra instancia ganó la carrera, se conserva su par.
        jdbc.update("INSERT INTO push_vapid_keys (id, public_key, private_key) VALUES (1, ?, ?) ON CONFLICT (id) DO NOTHING",
                generated.publicKey(), generated.privateKey());
        return jdbc.queryForObject("SELECT public_key, private_key FROM push_vapid_keys WHERE id = 1",
                (rs, row) -> new VapidKeys(rs.getString(1), rs.getString(2)));
    }
}

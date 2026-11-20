package com.oratoria.backend.notification;

import com.oratoria.backend.notification.WebPushCrypto.VapidKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Claves VAPID del servidor. Si llegan por variables de entorno se usan esas; si no, se generan una vez y se
 * guardan en la base para que sobrevivan reinicios (cambiarlas invalidaría todas las suscripciones).
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
        if (!configuredPublic.isEmpty() && !configuredPrivate.isEmpty()) return new VapidKeys(configuredPublic, configuredPrivate);
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

    /** Tras restaurar un respaldo la tabla puede traer otro par. */
    public void forget() { cached = null; }
}

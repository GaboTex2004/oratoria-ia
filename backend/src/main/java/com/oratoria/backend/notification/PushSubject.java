package com.oratoria.backend.notification;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El «sub» de VAPID identifica al responsable del servidor; Apple y Mozilla exigen un mailto: o https: real. */
final class PushSubject {
    private static final Logger log = LoggerFactory.getLogger(PushSubject.class);

    private PushSubject() {}

    static String validate(String subject) {
        String value = subject == null ? "" : subject.trim();
        boolean mailto = value.startsWith("mailto:") && value.indexOf('@') > 7;
        boolean https = value.startsWith("https://") && URI.create(value).getHost() != null;
        if (!mailto && !https) {
            throw new IllegalStateException("PUSH_SUBJECT debe ser un mailto: o una URL https:// del responsable del servidor.");
        }
        String domain = mailto ? value.substring(value.indexOf('@') + 1) : URI.create(value).getHost();
        if (domain.equals("localhost") || domain.endsWith(".local") || domain.endsWith(".test") || domain.endsWith(".example")) {
            log.warn("PUSH_SUBJECT usa un dominio de prueba ({}); algunos servicios push (Apple) pueden rechazar los envíos. "
                    + "Configura un correo o URL real en producción.", domain);
        }
        return value;
    }
}

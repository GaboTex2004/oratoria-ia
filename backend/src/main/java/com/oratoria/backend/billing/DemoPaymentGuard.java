package com.oratoria.backend.billing;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class DemoPaymentGuard {
    private final Environment environment;
    private final boolean configured;
    public DemoPaymentGuard(Environment environment,
            @Value("${app.billing.demo-enabled:false}") boolean configured) {
        this.environment = environment; this.configured = configured;
    }
    public boolean enabled() {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        return configured && !profiles.contains("prod") && !profiles.contains("production")
                && profiles.stream().anyMatch(p -> p.equals("dev") || p.equals("test") || p.equals("demo"));
    }
    public void requireEnabled() {
        if (!enabled()) throw new AccessDeniedException("El simulador de pagos está deshabilitado en este entorno.");
    }
    public void requireTimeTravel() {
        requireEnabled();
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch(p -> p.equals("dev") || p.equals("test")))
            throw new AccessDeniedException("El avance de tiempo solo está habilitado en desarrollo/pruebas.");
    }
}

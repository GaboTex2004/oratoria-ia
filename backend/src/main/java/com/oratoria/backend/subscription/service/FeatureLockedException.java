package com.oratoria.backend.subscription.service;

import com.oratoria.backend.subscription.entity.Feature;
import org.springframework.security.access.AccessDeniedException;

/** Raised when the user's current plan does not include a feature; mapped to 403 like any access denial. */
public class FeatureLockedException extends AccessDeniedException {
    public FeatureLockedException(Feature feature) {
        super("«" + feature.label() + "» está disponible en el plan Premium. Mejora tu plan para usarlo.");
    }
}

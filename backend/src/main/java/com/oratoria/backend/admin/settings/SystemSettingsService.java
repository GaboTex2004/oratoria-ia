package com.oratoria.backend.admin.settings;

import com.oratoria.backend.shared.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {
    public static final String REGISTRATION_ENABLED = "registration.enabled";
    public static final String AUDIO_MAX_DURATION = "audio.max-duration-seconds";
    public static final String ANNOUNCEMENT = "announcement.message";
    private static final int MIN_DURATION = 15;
    private static final int MAX_DURATION = 600;
    private static final int MAX_ANNOUNCEMENT = 300;

    private final SystemSettingRepository settings;
    private final int defaultAudioMaxDuration;

    public SystemSettingsService(SystemSettingRepository settings,
            @Value("${app.audio.max-duration-seconds:300}") int defaultAudioMaxDuration) {
        this.settings = settings;
        this.defaultAudioMaxDuration = defaultAudioMaxDuration;
    }

    @Transactional(readOnly = true)
    public boolean registrationEnabled() { return Boolean.parseBoolean(value(REGISTRATION_ENABLED)); }

    @Transactional(readOnly = true)
    public int audioMaxDurationSeconds() { return Integer.parseInt(value(AUDIO_MAX_DURATION)); }

    @Transactional(readOnly = true)
    public String announcement() { return value(ANNOUNCEMENT); }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : defaults().keySet()) values.put(key, value(key));
        return values;
    }

    /** Validates every entry first so an invalid request saves nothing. */
    @Transactional
    public void update(Map<String, String> changes) {
        if (changes == null || changes.isEmpty()) throw new BadRequestException("No se enviaron parámetros.");
        Map<String, String> normalized = new LinkedHashMap<>();
        changes.forEach((key, raw) -> normalized.put(key, validate(key, raw)));
        normalized.forEach((key, value) -> settings.findById(key)
                .ifPresentOrElse(setting -> setting.change(value), () -> settings.save(new SystemSetting(key, value))));
    }

    private String validate(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (key) {
            case REGISTRATION_ENABLED -> {
                if (!value.equals("true") && !value.equals("false")) throw new BadRequestException("registration.enabled debe ser true o false.");
            }
            case AUDIO_MAX_DURATION -> {
                int seconds;
                try { seconds = Integer.parseInt(value); } catch (NumberFormatException e) { seconds = -1; }
                if (seconds < MIN_DURATION || seconds > MAX_DURATION) {
                    throw new BadRequestException("La duración máxima debe estar entre " + MIN_DURATION + " y " + MAX_DURATION + " segundos.");
                }
                value = String.valueOf(seconds);
            }
            case ANNOUNCEMENT -> {
                if (value.length() > MAX_ANNOUNCEMENT) throw new BadRequestException("El aviso no puede superar " + MAX_ANNOUNCEMENT + " caracteres.");
            }
            default -> throw new BadRequestException("Parámetro desconocido: " + key);
        }
        return value;
    }

    private String value(String key) {
        return settings.findById(key).map(SystemSetting::getValue).orElseGet(() -> defaults().get(key));
    }

    private Map<String, String> defaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(REGISTRATION_ENABLED, "true");
        defaults.put(AUDIO_MAX_DURATION, String.valueOf(defaultAudioMaxDuration));
        defaults.put(ANNOUNCEMENT, "");
        return defaults;
    }
}

package com.oratoria.backend.admin.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SystemSettingsStartupTests {
    @Test void invalidConfiguredRecordingLimitFailsAtStartup() {
        assertThatThrownBy(() -> new SystemSettingsService(null, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUDIO_MAX_DURATION_SECONDS");
        assertThatThrownBy(() -> new SystemSettingsService(null, 1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void validConfiguredRecordingLimitIsAccepted() {
        assertThatCode(() -> new SystemSettingsService(null, 300)).doesNotThrowAnyException();
    }
}

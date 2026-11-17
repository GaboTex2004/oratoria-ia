package com.oratoria.backend.admin.settings;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "system_settings")
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;
    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemSetting() {}

    public SystemSetting(String key, String value) {
        this.key = key;
        change(value);
    }

    public void change(String value) { this.value = value; this.updatedAt = Instant.now(); }

    public String getKey() { return key; }
    public String getValue() { return value; }
}

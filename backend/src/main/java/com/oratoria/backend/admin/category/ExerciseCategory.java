package com.oratoria.backend.admin.category;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "exercise_categories")
public class ExerciseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 300)
    private String description;
    @Column(nullable = false)
    private boolean active = true;

    protected ExerciseCategory() {}

    public ExerciseCategory(String code, String name, String description, boolean active) {
        this.code = code;
        update(name, description, active);
    }

    public void update(String name, String description, boolean active) {
        this.name = name; this.description = description; this.active = active;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}

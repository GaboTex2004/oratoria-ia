package com.oratoria.backend.user.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_training_preferences")
public class TrainingPreferences {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 40)
    private String goal;

    @Column(name = "experience_level", nullable = false, length = 20)
    private String experienceLevel = "BASIC";

    @Column(name = "priority_skills", nullable = false, length = 300)
    private String prioritySkills = "";

    @Column(name = "preferred_training_types", nullable = false, length = 300)
    private String preferredTrainingTypes = "";

    @Column(name = "preferred_style", length = 40)
    private String preferredStyle;

    @Column(name = "weekly_goal", nullable = false)
    private int weeklyGoal = 3;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingPreferences() {
    }

    /** Unsaved defaults for a user who never configured preferences. */
    public TrainingPreferences(UUID userId) {
        this.userId = userId;
    }

    public void update(String goal, String experienceLevel, List<String> prioritySkills,
                       List<String> preferredTrainingTypes, String preferredStyle, int weeklyGoal) {
        this.goal = goal;
        this.experienceLevel = experienceLevel;
        this.prioritySkills = String.join(",", prioritySkills);
        this.preferredTrainingTypes = String.join(",", preferredTrainingTypes);
        this.preferredStyle = preferredStyle;
        this.weeklyGoal = weeklyGoal;
        this.updatedAt = Instant.now();
    }

    public void selectStyle(String preferredStyle) {
        this.preferredStyle = preferredStyle;
        this.updatedAt = Instant.now();
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
    }

    public UUID getUserId() { return userId; }
    public String getGoal() { return goal; }
    public String getExperienceLevel() { return experienceLevel; }
    public List<String> getPrioritySkills() { return split(prioritySkills); }
    public List<String> getPreferredTrainingTypes() { return split(preferredTrainingTypes); }
    public String getPreferredStyle() { return preferredStyle; }
    public int getWeeklyGoal() { return weeklyGoal; }
    public Instant getUpdatedAt() { return updatedAt; }
}

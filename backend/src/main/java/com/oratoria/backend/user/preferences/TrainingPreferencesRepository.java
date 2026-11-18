package com.oratoria.backend.user.preferences;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPreferencesRepository extends JpaRepository<TrainingPreferences, UUID> {
}

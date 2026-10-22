package com.oratoria.backend.session.dto;

import com.oratoria.backend.training.TrainingType;
import java.util.UUID;

public record CreateSessionRequest(UUID exerciseId, TrainingType trainingType) {
}

package com.oratoria.backend.user.dto;

import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.user.entity.UserRole;
import java.util.UUID;

public record UserResponse(UUID id, String firstName, String lastName, String email,
                           UserRole role, PlanCode plan) {
}

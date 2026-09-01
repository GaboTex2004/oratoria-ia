package com.oratoria.backend.auth.dto;

import com.oratoria.backend.user.dto.UserResponse;

public record AuthResponse(String token, UserResponse user) {
    @Override
    public String toString() { return "AuthResponse[token=***, user=" + user + "]"; }
}

package com.oratoria.backend.auth.controller;

import com.oratoria.backend.admin.audit.AuditAction;
import com.oratoria.backend.admin.audit.AuditService;
import com.oratoria.backend.auth.dto.AuthResponse;
import com.oratoria.backend.auth.dto.LoginRequest;
import com.oratoria.backend.auth.dto.RegisterRequest;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuditService audit;

    public AuthController(AuthService authService, AuditService audit) {
        this.authService = authService;
        this.audit = audit;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResponse response = authService.register(request);
        audit.record(response.user().email(), AuditAction.REGISTER, "user", String.valueOf(response.user().id()), null, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String email = request.email() == null ? null : request.email().trim().toLowerCase();
        try {
            AuthResponse response = authService.login(request);
            audit.record(email, AuditAction.LOGIN_SUCCESS, "user", String.valueOf(response.user().id()), null, http.getRemoteAddr());
            return response;
        } catch (InvalidCredentialsException exception) {
            audit.record(email, AuditAction.LOGIN_FAILED, "user", null, "Credenciales inválidas o cuenta deshabilitada.", http.getRemoteAddr());
            throw exception;
        }
    }
}

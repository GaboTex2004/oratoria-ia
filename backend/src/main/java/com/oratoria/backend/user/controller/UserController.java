package com.oratoria.backend.user.controller;

import com.oratoria.backend.auth.service.AuthService;
import com.oratoria.backend.user.dto.UserResponse;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;
    public UserController(AuthService authService) { this.authService = authService; }

    @GetMapping("/me")
    UserResponse me(Principal principal) { return authService.currentUser(principal.getName()); }
}

package com.oratoria.backend.user.controller;

import com.oratoria.backend.auth.service.AuthService;
import com.oratoria.backend.user.dto.UserResponse;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import com.oratoria.backend.user.service.ProfileService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;
    private final ProfileService profiles;
    public UserController(AuthService authService, ProfileService profiles) {
        this.authService = authService;
        this.profiles = profiles;
    }

    @GetMapping("/me")
    UserResponse me(Principal principal) { return authService.currentUser(principal.getName()); }

    @PutMapping("/me")
    UserResponse update(Principal principal, @Valid @RequestBody ProfileService.ProfileUpdate request) {
        return profiles.update(principal.getName(), request);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(Principal principal, @Valid @RequestBody ProfileService.PasswordUpdate request) {
        profiles.changePassword(principal.getName(), request);
    }
}

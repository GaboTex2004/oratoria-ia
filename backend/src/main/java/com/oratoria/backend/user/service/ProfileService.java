package com.oratoria.backend.user.service;

import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.auth.service.AuthService;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.user.dto.UserResponse;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;

    public ProfileService(UserAccountRepository users, PasswordEncoder encoder, AuthService auth) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
    }

    @Transactional
    public UserResponse update(String email, ProfileUpdate request) {
        UserAccount user = find(email);
        user.updateProfile(request.firstName(), request.lastName());
        users.saveAndFlush(user);
        return auth.currentUser(email);
    }

    @Transactional
    public void changePassword(String email, PasswordUpdate request) {
        com.oratoria.backend.shared.PasswordPolicy.validate(request.newPassword());
        if (!com.oratoria.backend.shared.PasswordPolicy.fitsBcrypt(request.currentPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta.");
        }
        UserAccount user = find(email);
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta.");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La nueva contraseña debe ser diferente.");
        }
        user.changePassword(encoder.encode(request.newPassword()));
        users.save(user);
    }

    private UserAccount find(String email) {
        return users.findByEmailIgnoreCase(email).filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
    }

    public record ProfileUpdate(@NotBlank @Size(max = 80) String firstName,
                                @NotBlank @Size(max = 80) String lastName) {}
    public record PasswordUpdate(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotBlank @Size(min = 8, max = 72) String newPassword) {
        @Override public String toString() { return "PasswordUpdate[passwords=***]"; }
    }
}

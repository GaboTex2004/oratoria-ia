package com.oratoria.backend.shared;

import com.oratoria.backend.shared.exception.BadRequestException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static boolean fitsBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public static void validate(String password) {
        if (password == null || password.length() < 8 || !fitsBcrypt(password)) {
            throw new BadRequestException("La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.");
        }
    }
}

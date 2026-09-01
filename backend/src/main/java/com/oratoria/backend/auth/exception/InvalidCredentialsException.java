package com.oratoria.backend.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Correo o contraseña incorrectos."); }
}

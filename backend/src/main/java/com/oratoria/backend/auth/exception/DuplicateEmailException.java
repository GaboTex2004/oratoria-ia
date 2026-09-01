package com.oratoria.backend.auth.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() { super("Ya existe una cuenta con ese correo electrónico."); }
}

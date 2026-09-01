package com.oratoria.backend.auth.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @GetMapping("/status")
    Map<String, String> status() { return Map.of("status", "ok", "message", "Acceso de administrador confirmado."); }
}

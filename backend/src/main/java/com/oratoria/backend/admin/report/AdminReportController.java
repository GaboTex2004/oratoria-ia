package com.oratoria.backend.admin.report;

import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {
    private final AdminReportService reports;
    private final AdminReportPdf pdf;

    public AdminReportController(AdminReportService reports, AdminReportPdf pdf) { this.reports = reports; this.pdf = pdf; }

    @GetMapping("/exercises")
    AdminReportService.ExercisesReport exercises(@RequestParam(defaultValue = "30") int days) { return reports.exercises(days); }

    @GetMapping("/difficulties")
    AdminReportService.DifficultiesReport difficulties(@RequestParam(defaultValue = "30") int days) { return reports.difficulties(days); }

    @GetMapping("/exercises.pdf")
    ResponseEntity<byte[]> exercisesPdf(@RequestParam(defaultValue = "30") int days) {
        return file("ejercicios-completados", pdf.exercises(reports.exercises(days)));
    }

    @GetMapping("/difficulties.pdf")
    ResponseEntity<byte[]> difficultiesPdf(@RequestParam(defaultValue = "30") int days) {
        return file("dificultades-frecuentes", pdf.difficulties(reports.difficulties(days)));
    }

    private static ResponseEntity<byte[]> file(String name, byte[] content) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "-" + LocalDate.now() + ".pdf\"")
                .body(content);
    }
}

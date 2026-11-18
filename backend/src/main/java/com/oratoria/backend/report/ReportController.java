package com.oratoria.backend.report;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;
    private final PdfReportRenderer renderer;

    public ReportController(ReportService reports, PdfReportRenderer renderer) {
        this.reports = reports; this.renderer = renderer;
    }

    @GetMapping("/performance")
    ReportService.PerformanceReport performance(Principal principal, @RequestParam(defaultValue = "30") int days) {
        return reports.build(principal.getName(), days);
    }

    @GetMapping("/performance.pdf")
    ResponseEntity<byte[]> pdf(Principal principal, @RequestParam(defaultValue = "30") int days) {
        byte[] pdf = renderer.render(reports.build(principal.getName(), days));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-oratoria-" + LocalDate.now(PdfReportRenderer.ZONE) + ".pdf\"")
                .body(pdf);
    }
}

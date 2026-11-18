package com.oratoria.backend.admin.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    AuditService.AuditPage search(@RequestParam(required = false) String actor, @RequestParam(required = false) String action,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                  @RequestParam(defaultValue = "0") int page) {
        return audit.search(actor, action, from, to, page);
    }

    @GetMapping("/actions")
    List<AuditService.ActionOption> actions() { return audit.actions(); }
}

package com.oratoria.backend.admin.backup;

import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/admin/backups")
public class BackupController {
    private final BackupService backups;

    public BackupController(BackupService backups) { this.backups = backups; }

    @GetMapping
    List<BackupService.BackupView> list() { return backups.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BackupService.BackupView create(Principal principal, @jakarta.validation.Valid @RequestBody(required = false) CreateRequest request) {
        return backups.create(principal.getName(), request == null ? null : request.note());
    }

    @GetMapping("/{id}/download")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        BackupService.BackupFile file = backups.download(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { backups.delete(id); }

    @PostMapping("/{id}/restore")
    BackupService.RestoreResult restore(Principal principal, @PathVariable UUID id, @RequestBody RestoreRequest request) {
        return backups.restoreStored(principal.getName(), id, request.confirm());
    }

    @PostMapping(path = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BackupService.RestoreResult restoreUpload(Principal principal, @RequestParam("file") MultipartFile file,
                                              @RequestParam("confirm") String confirm) throws IOException {
        return backups.restoreUploaded(principal.getName(), file.getBytes(), confirm);
    }

    public record CreateRequest(@Size(max = 200) String note) {}

    public record RestoreRequest(String confirm) {}
}

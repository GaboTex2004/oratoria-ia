package com.oratoria.backend.session.controller;

import com.oratoria.backend.session.dto.CreateSessionRequest;
import com.oratoria.backend.session.dto.SessionResponse;
import com.oratoria.backend.session.service.TrainingSessionService;
import com.oratoria.backend.session.service.TrainingSessionService.SessionAudioResource;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sessions")
public class TrainingSessionController {
    private final TrainingSessionService sessionService;

    public TrainingSessionController(TrainingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    ResponseEntity<SessionResponse> create(
            Principal principal,
            @RequestBody(required = false) CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(principal.getName(), request));
    }

    @GetMapping
    List<SessionResponse> listMySessions(Principal principal) {
        return sessionService.listUserSessions(principal.getName());
    }

    @GetMapping("/{id}")
    SessionResponse getById(Principal principal, @PathVariable UUID id) {
        return sessionService.getUserSession(principal.getName(), id);
    }

    @PostMapping(path = "/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SessionResponse complete(
            Principal principal,
            @PathVariable UUID id,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("durationSeconds") Integer durationSeconds) {
        return sessionService.completeSession(principal.getName(), id, audio, durationSeconds);
    }

    @GetMapping("/{id}/audio")
    ResponseEntity<Resource> downloadAudio(Principal principal, @PathVariable UUID id) {
        SessionAudioResource audio = sessionService.loadSessionAudio(principal.getName(), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .contentType(MediaType.parseMediaType(audio.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + audio.filename() + "\"")
                .body(audio.resource());
    }
}

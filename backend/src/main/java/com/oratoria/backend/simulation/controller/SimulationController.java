package com.oratoria.backend.simulation.controller;
import com.oratoria.backend.simulation.dto.SimulationDtos.*;
import com.oratoria.backend.simulation.service.SimulationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/simulations")
public class SimulationController {
 private final SimulationService service; public SimulationController(SimulationService service){this.service=service;}
 @GetMapping("/scenarios") List<ScenarioResponse> scenarios(){return service.listScenarios();}
 @PostMapping ResponseEntity<SessionResponse> create(Principal p,@Valid @RequestBody CreateRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(p.getName(),r));}
 @GetMapping List<SessionResponse> list(Principal p){return service.list(p.getName());}
 @GetMapping("/{id}") SessionResponse get(Principal p,@PathVariable UUID id){return service.get(p.getName(),id);}
 @PostMapping(path="/{id}/respond",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) SessionResponse respond(Principal p,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@RequestParam("audio") MultipartFile audio,@RequestParam Integer durationSeconds){return service.respond(p.getName(),id,key,audio,durationSeconds);}
 @PostMapping("/{id}/finish") SessionResponse finish(Principal p,@PathVariable UUID id){return service.finish(p.getName(),id);}
 @GetMapping("/{id}/feedback") FeedbackResponse feedback(Principal p,@PathVariable UUID id){return service.feedback(p.getName(),id);}
 @GetMapping("/{id}/turns/{turnId}/audio") ResponseEntity<Resource> audio(Principal p,@PathVariable UUID id,@PathVariable UUID turnId){var a=service.audio(p.getName(),id,turnId);return ResponseEntity.ok().cacheControl(CacheControl.noStore().mustRevalidate()).contentType(MediaType.parseMediaType(a.mimeType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+a.filename()+"\"").body(a.resource());}
}

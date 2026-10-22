package com.oratoria.backend.session.service;

import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudioStorageService {
    private static final Map<String, String> ALLOWED_MIME_EXTENSIONS = Map.ofEntries(
            Map.entry("audio/webm", ".webm"),
            Map.entry("video/webm", ".webm"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/wave", ".wav"),
            Map.entry("audio/x-wav", ".wav"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/x-m4a", ".m4a"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/mp3", ".mp3"),
            Map.entry("audio/aac", ".aac")
    );

    private final Path baseDir;
    private final long maxSizeBytes;
    private final int maxDurationSeconds;

    public AudioStorageService(
            @Value("${app.audio.storage-dir:./data/audio}") String storageDir,
            @Value("${app.audio.max-size-bytes:15728640}") long maxSizeBytes,
            @Value("${app.audio.max-duration-seconds:300}") int maxDurationSeconds) {
        if (maxSizeBytes <= 0) {
            throw new IllegalStateException("AUDIO_MAX_SIZE_BYTES debe ser mayor que cero.");
        }
        if (maxDurationSeconds <= 0) {
            throw new IllegalStateException("AUDIO_MAX_DURATION_SECONDS debe ser mayor que cero.");
        }
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public StoredAudio store(UUID userId, UUID sessionId, MultipartFile file, Integer durationSeconds) {
        validateDuration(durationSeconds);
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BadRequestException("El archivo de audio está vacío o no fue enviado.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("El archivo de audio supera el tamaño máximo permitido.");
        }

        String normalizedMime = normalizeMimeType(file.getContentType());
        String extension = ALLOWED_MIME_EXTENSIONS.get(normalizedMime);
        if (extension == null) {
            throw new BadRequestException("Formato de audio no permitido. Usa WebM, OGG, WAV, MP4/M4A o MP3.");
        }

        String relativeKey = userId + "/" + sessionId + extension;
        Path targetPath = resolveSafePath(relativeKey);
        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo almacenar el archivo de audio.", exception);
        }

        String safeOriginalName = sanitizeFilename(file.getOriginalFilename(), sessionId + extension);
        return new StoredAudio(relativeKey, normalizedMime, file.getSize(), safeOriginalName);
    }

    public Resource loadAsResource(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            throw new ResourceNotFoundException("La sesión no tiene un archivo de audio asociado.");
        }
        Path filePath = resolveSafePath(relativeKey);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResourceNotFoundException("El archivo de audio de la sesión no fue encontrado.");
        }
        return new FileSystemResource(filePath);
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    private void validateDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds < 1) {
            throw new BadRequestException("La duración de la grabación debe ser de al menos 1 segundo.");
        }
        if (durationSeconds > maxDurationSeconds) {
            throw new BadRequestException(
                    "La duración de la grabación supera el límite máximo de " + maxDurationSeconds + " segundos.");
        }
    }

    private String normalizeMimeType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return "";
        }
        int semicolon = rawContentType.indexOf(';');
        String base = semicolon >= 0 ? rawContentType.substring(0, semicolon) : rawContentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private Path resolveSafePath(String relativeKey) {
        Path resolved = baseDir.resolve(relativeKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BadRequestException("Ruta de almacenamiento de audio inválida.");
        }
        return resolved;
    }

    private String sanitizeFilename(String rawFilename, String fallback) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return fallback;
        }
        String sanitized = rawFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() > 200 ? sanitized.substring(0, 200) : sanitized;
    }

    public record StoredAudio(String storagePath, String mimeType, long sizeBytes, String originalFilename) {
    }
}

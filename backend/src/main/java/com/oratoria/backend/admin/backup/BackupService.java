package com.oratoria.backend.admin.backup;

import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Logical backups of the application database (all public tables except schema history, the audit log and the
 * backup catalog itself). Rows are exported with row_to_json and loaded back with json_populate_record, so
 * PostgreSQL does every type conversion. Recorded audio lives in the file storage and is not part of the backup.
 */
@Service
public class BackupService {
    static final String FORMAT = "oratoria-backup";
    static final int FORMAT_VERSION = 1;
    public static final String CONFIRMATION = "RESTAURAR";
    static final Set<String> EXCLUDED = Set.of("flyway_schema_history", "audit_log", "system_backups");
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("America/La_Paz"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final EntityManager entityManager;
    private final Path directory;

    public BackupService(JdbcTemplate jdbc, ObjectMapper json, EntityManager entityManager,
                         @Value("${app.backup.dir:./data/backups}") String directory) {
        this.jdbc = jdbc; this.json = json; this.entityManager = entityManager;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<BackupView> list() {
        return jdbc.query("SELECT * FROM system_backups ORDER BY created_at DESC", (rs, i) -> new BackupView(
                rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"),
                rs.getString("file_name"), rs.getLong("size_bytes"), rs.getInt("table_count"), rs.getLong("row_count"),
                rs.getString("schema_version"), rs.getString("note")));
    }

    @Transactional
    public BackupView create(String actor, String note) {
        entityManager.flush();
        List<String> tables = orderedTables();
        String schema = schemaVersion();
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("{\"format\":\"").append(FORMAT).append("\",\"formatVersion\":").append(FORMAT_VERSION)
                .append(",\"schemaVersion\":").append(json.writeValueAsString(schema))
                .append(",\"createdAt\":\"").append(now).append("\",\"createdBy\":").append(json.writeValueAsString(actor))
                .append(",\"tables\":{");
        long rows = 0;
        for (int t = 0; t < tables.size(); t++) {
            String table = tables.get(t);
            List<String> data = jdbc.queryForList("SELECT row_to_json(t)::text FROM " + quote(table) + " t", String.class);
            out.append(t == 0 ? "" : ",").append('"').append(table).append("\":[").append(String.join(",", data)).append(']');
            rows += data.size();
        }
        out.append("}}");
        byte[] content = out.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = "respaldo-oratoria-" + FILE_TIME.format(now) + "-" + id.toString().substring(0, 8) + ".json";
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo escribir el respaldo.", exception);
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        jdbc.update("""
                INSERT INTO system_backups (id, created_at, created_by, file_name, size_bytes, table_count, row_count, schema_version, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, java.sql.Timestamp.from(now), actor, fileName, (long) content.length,
                tables.size(), rows, schema, cleanNote);
        return new BackupView(id, now, actor, fileName, content.length, tables.size(), rows, schema, cleanNote);
    }

    @Transactional(readOnly = true)
    public BackupFile download(UUID id) {
        BackupView backup = find(id);
        return new BackupFile(backup.fileName(), read(backup));
    }

    @Transactional
    public void delete(UUID id) {
        BackupView backup = find(id);
        jdbc.update("DELETE FROM system_backups WHERE id = ?", id);
        try {
            Files.deleteIfExists(file(backup.fileName()));
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo eliminar el archivo del respaldo.", exception);
        }
    }

    @Transactional
    public RestoreResult restoreStored(String actor, UUID id, String confirmation) {
        requireConfirmation(confirmation);
        return restore(actor, read(find(id)));
    }

    @Transactional
    public RestoreResult restoreUploaded(String actor, byte[] content, String confirmation) {
        requireConfirmation(confirmation);
        if (content == null || content.length == 0) throw new BadRequestException("Selecciona un archivo de respaldo.");
        return restore(actor, content);
    }

    /** Validates the whole file first, saves the current state as a safety backup, then reloads every table. */
    private RestoreResult restore(String actor, byte[] content) {
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (RuntimeException exception) {
            throw new BadRequestException("El archivo no es un respaldo válido de OratoriaAI.");
        }
        if (root == null || !FORMAT.equals(root.path("format").asString("")) || root.path("formatVersion").asInt(0) != FORMAT_VERSION
                || !root.path("tables").isObject()) {
            throw new BadRequestException("El archivo no es un respaldo válido de OratoriaAI.");
        }
        String current = schemaVersion();
        String version = root.path("schemaVersion").asString("");
        if (!current.equals(version)) {
            throw new BadRequestException("El respaldo corresponde a otra versión del esquema (" + version
                    + "); la base actual está en la versión " + current + ".");
        }
        List<String> tables = orderedTables();
        JsonNode data = root.get("tables");
        for (String name : data.propertyNames()) {
            if (!tables.contains(name)) throw new BadRequestException("El respaldo contiene una tabla desconocida: " + name);
            if (!data.get(name).isArray()) throw new BadRequestException("El respaldo tiene un formato inválido en la tabla " + name);
        }

        create(actor, "Automático antes de restaurar");
        entityManager.flush();
        jdbc.execute("TRUNCATE " + String.join(", ", tables.stream().map(BackupService::quote).toList()));
        long rows = 0;
        for (String table : tables) {
            JsonNode tableRows = data.get(table);
            if (tableRows == null || tableRows.isEmpty()) continue;
            List<Object[]> batch = new ArrayList<>();
            for (JsonNode row : tableRows) batch.add(new Object[]{row.toString()});
            jdbc.batchUpdate("INSERT INTO " + quote(table) + " SELECT * FROM json_populate_record(NULL::" + quote(table) + ", ?::json)", batch);
            rows += batch.size();
        }
        entityManager.clear();
        return new RestoreResult(tables.size(), rows);
    }

    /** Restorable tables, parents before children, so foreign keys hold while rows are inserted. */
    List<String> orderedTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name""", String.class)
                .stream().filter(name -> !EXCLUDED.contains(name)).toList();
        for (String table : tables) {
            if (!SAFE_NAME.matcher(table).matches()) throw new IllegalStateException("Nombre de tabla no soportado: " + table);
        }
        Map<String, Set<String>> parents = new HashMap<>();
        jdbc.query("""
                SELECT child.relname AS child, parent.relname AS parent FROM pg_constraint c
                JOIN pg_class child ON child.oid = c.conrelid JOIN pg_class parent ON parent.oid = c.confrelid
                JOIN pg_namespace n ON n.oid = child.relnamespace
                WHERE c.contype = 'f' AND n.nspname = 'public'""", rs -> {
            String child = rs.getString("child");
            String parent = rs.getString("parent");
            if (!child.equals(parent)) parents.computeIfAbsent(child, key -> new HashSet<>()).add(parent);
        });
        List<String> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        for (String table : tables) visit(table, tables, parents, visiting, ordered);
        return ordered;
    }

    private static void visit(String table, List<String> tables, Map<String, Set<String>> parents, Set<String> visiting, List<String> ordered) {
        if (ordered.contains(table) || !tables.contains(table)) return;
        if (!visiting.add(table)) throw new IllegalStateException("Dependencia circular entre tablas: " + table);
        for (String parent : parents.getOrDefault(table, Set.of())) visit(parent, tables, parents, visiting, ordered);
        visiting.remove(table);
        ordered.add(table);
    }

    private String schemaVersion() {
        return jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
    }

    private BackupView find(UUID id) {
        return list().stream().filter(backup -> backup.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("El respaldo no existe."));
    }

    private byte[] read(BackupView backup) {
        try {
            return Files.readAllBytes(file(backup.fileName()));
        } catch (IOException exception) {
            throw new ResourceNotFoundException("El archivo del respaldo no está en el servidor.");
        }
    }

    private Path file(String name) {
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory)) throw new BadRequestException("Nombre de archivo inválido.");
        return path;
    }

    private static void requireConfirmation(String confirmation) {
        if (!CONFIRMATION.equals(confirmation)) {
            throw new BadRequestException("Para restaurar escribe " + CONFIRMATION + ": se reemplazarán todos los datos actuales.");
        }
    }

    private static String quote(String table) { return '"' + table + '"'; }

    public record BackupView(UUID id, Instant createdAt, String createdBy, String fileName, long sizeBytes, int tables,
                             long rows, String schemaVersion, String note) {}

    public record BackupFile(String fileName, byte[] content) {}

    public record RestoreResult(int tables, long rows) {}
}

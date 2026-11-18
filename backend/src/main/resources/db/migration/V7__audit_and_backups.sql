-- Bitácora general de acciones. Sin claves foráneas a propósito: debe sobrevivir a la restauración de un respaldo
-- y conservar el correo de quien actuó aunque la cuenta cambie o desaparezca.
CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    actor_email varchar(320),
    action varchar(60) NOT NULL,
    entity_type varchar(60),
    entity_id varchar(100),
    detail varchar(500),
    ip_address varchar(64)
);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (lower(actor_email));
CREATE INDEX idx_audit_log_action ON audit_log (action);

-- Metadatos de los respaldos generados desde la app; el contenido vive en archivos del directorio de respaldos.
CREATE TABLE system_backups (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(320),
    file_name varchar(200) NOT NULL,
    size_bytes bigint NOT NULL,
    table_count integer NOT NULL,
    row_count bigint NOT NULL,
    schema_version varchar(20) NOT NULL,
    note varchar(200)
);

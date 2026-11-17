CREATE TABLE exercise_categories (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL UNIQUE,
    name varchar(80) NOT NULL,
    description varchar(300),
    active boolean NOT NULL DEFAULT true
);

INSERT INTO exercise_categories (id, code, name, description, active) VALUES
    (gen_random_uuid(), 'CLARIDAD', 'Claridad', 'Dicción y comprensión del mensaje.', true),
    (gen_random_uuid(), 'RITMO', 'Ritmo', 'Velocidad del habla y uso de pausas.', true),
    (gen_random_uuid(), 'ENTONACION', 'Entonación', 'Variación vocal y énfasis.', true),
    (gen_random_uuid(), 'FLUIDEZ', 'Fluidez', 'Continuidad del discurso y control de muletillas.', true),
    (gen_random_uuid(), 'ESTRUCTURA', 'Estructura', 'Organización de las ideas.', true),
    (gen_random_uuid(), 'ARGUMENTACION', 'Argumentación', 'Defensa de ideas con evidencia.', true),
    (gen_random_uuid(), 'IMPROVISACION', 'Improvisación', 'Respuesta ante temas o preguntas inesperadas.', true),
    (gen_random_uuid(), 'NO_VERBAL', 'Comunicación no verbal', 'Postura, mirada y gestos.', true),
    (gen_random_uuid(), 'ESTILOS', 'Estilos de comunicación', 'Persuasivo, narrativo, dinámico y académico.', true);

CREATE TABLE system_settings (
    setting_key varchar(80) PRIMARY KEY,
    setting_value varchar(500) NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

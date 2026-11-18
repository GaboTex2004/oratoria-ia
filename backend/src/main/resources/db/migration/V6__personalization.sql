-- CU-19: objetivos y preferencias de entrenamiento por usuario.
CREATE TABLE user_training_preferences (
    user_id uuid PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    goal varchar(40),
    experience_level varchar(20) NOT NULL DEFAULT 'BASIC',
    priority_skills varchar(300) NOT NULL DEFAULT '',
    preferred_training_types varchar(300) NOT NULL DEFAULT '',
    preferred_style varchar(40),
    weekly_goal integer NOT NULL DEFAULT 3,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- CU-21: estilo de comunicación de un ejercicio y de la sesión que lo practica.
ALTER TABLE exercises ADD COLUMN communication_style varchar(40);
ALTER TABLE training_sessions ADD COLUMN communication_style varchar(40);

-- Additive migration: preserve all existing users, sessions and results.
ALTER TABLE app_users ADD COLUMN token_version integer NOT NULL DEFAULT 0;
ALTER TABLE exercises ADD COLUMN category varchar(60) NOT NULL DEFAULT 'ESTRUCTURA';
ALTER TABLE exercises ADD COLUMN difficulty varchar(30) NOT NULL DEFAULT 'BASIC';
ALTER TABLE exercises ADD COLUMN objective varchar(400) NOT NULL DEFAULT 'Desarrollar una intervención oral clara y estructurada.';
ALTER TABLE speech_analyses ADD COLUMN acoustic_metrics_json text;
CREATE TABLE body_analyses (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL UNIQUE REFERENCES training_sessions(id),
    result_json text NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX ix_sessions_user_created ON training_sessions(user_id, created_at);
CREATE INDEX ix_simulations_user_created ON simulation_sessions(user_id, created_at);
CREATE INDEX ix_subscriptions_user_status ON subscriptions(user_id, status);

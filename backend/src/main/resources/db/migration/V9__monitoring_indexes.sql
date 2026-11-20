-- Índices para el panel de monitoreo, que consulta cada pocos segundos las colas activas y la actividad reciente.
-- Parciales: solo cubren los trabajos pendientes o en curso, que son pocos aunque el historial crezca.
CREATE INDEX ix_speech_analyses_active ON speech_analyses (status, started_at) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX ix_speech_evaluations_active ON speech_evaluations (status, started_at) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX ix_speech_analyses_updated ON speech_analyses (updated_at);
CREATE INDEX ix_speech_evaluations_updated ON speech_evaluations (updated_at);
CREATE INDEX ix_sessions_created ON training_sessions (created_at);
CREATE INDEX ix_simulations_created ON simulation_sessions (created_at);

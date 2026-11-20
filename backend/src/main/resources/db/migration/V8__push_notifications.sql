-- Notificaciones push (Web Push + VAPID). El contenido de cada aviso viaja cifrado hasta el navegador;
-- el servicio push del navegador solo ve el destino, nunca el texto.

-- Par de claves VAPID del servidor. Se genera una sola vez al arrancar si no llega por variables de entorno.
CREATE TABLE push_vapid_keys (
    id smallint PRIMARY KEY CHECK (id = 1),
    public_key varchar(100) NOT NULL,
    private_key varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Un registro por navegador o dispositivo suscrito.
CREATE TABLE push_subscriptions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    endpoint varchar(1000) NOT NULL UNIQUE,
    p256dh varchar(200) NOT NULL,
    auth_secret varchar(100) NOT NULL,
    user_agent varchar(300),
    created_at timestamptz NOT NULL DEFAULT now(),
    last_success_at timestamptz,
    failure_count integer NOT NULL DEFAULT 0
);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

-- Qué avisos quiere recibir cada persona.
CREATE TABLE notification_settings (
    user_id uuid PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    practice_ready boolean NOT NULL DEFAULT true,
    daily_reminder boolean NOT NULL DEFAULT false,
    reminder_hour smallint NOT NULL DEFAULT 19 CHECK (reminder_hour BETWEEN 0 AND 23),
    time_zone varchar(60) NOT NULL DEFAULT 'America/La_Paz',
    last_reminder_on date,
    updated_at timestamptz NOT NULL DEFAULT now()
);

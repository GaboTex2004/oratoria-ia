ALTER TABLE plans ADD COLUMN price NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0);
ALTER TABLE plans ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BOB' CHECK (currency = 'BOB');
UPDATE plans SET price = 49.00 WHERE code = 'PREMIUM';
ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check CHECK (status IN ('ACTIVE','CANCELED','EXPIRED','PENDING_PAYMENT','REJECTED'));
ALTER TABLE subscriptions ADD COLUMN auto_renew BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriptions ADD COLUMN demo_managed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriptions ADD COLUMN grace_until TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN renewal_scenario VARCHAR(30) NOT NULL DEFAULT 'DEMO_APPROVED';
CREATE TABLE payment_orders (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id),
 subscription_id UUID NOT NULL REFERENCES subscriptions(id),
 amount NUMERIC(12,2) NOT NULL CHECK (amount > 0), currency VARCHAR(3) NOT NULL CHECK (currency = 'BOB'),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('CHECKOUT','RENEWAL')),
 idempotency_key VARCHAR(100) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 period_start TIMESTAMPTZ, period_end TIMESTAMPTZ,
 UNIQUE(user_id, idempotency_key), UNIQUE(subscription_id, kind, period_start)
);
CREATE INDEX payment_orders_user_created ON payment_orders(user_id, created_at DESC);
CREATE TABLE payment_transactions (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES payment_orders(id),
 scenario VARCHAR(30) NOT NULL CHECK (scenario IN ('DEMO_APPROVED','DEMO_DECLINED','DEMO_PENDING')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED','REJECTED','PENDING')),
 authorization_code VARCHAR(40), created_at TIMESTAMPTZ NOT NULL, resolved_at TIMESTAMPTZ,
 CHECK ((status = 'APPROVED' AND authorization_code IS NOT NULL) OR (status <> 'APPROVED' AND authorization_code IS NULL))
);
CREATE TABLE payment_events (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES payment_orders(id),
 event_type VARCHAR(50) NOT NULL, actor_id UUID NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL, detail VARCHAR(300) NOT NULL
);
CREATE TABLE billing_audit_logs (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), actor_id UUID NOT NULL REFERENCES app_users(id),
 action VARCHAR(50) NOT NULL, reference_id UUID, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE billing_demo_clocks (
 user_id UUID PRIMARY KEY REFERENCES app_users(id), simulated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE usage_records (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), kind VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX usage_records_user_time ON usage_records(user_id, created_at);

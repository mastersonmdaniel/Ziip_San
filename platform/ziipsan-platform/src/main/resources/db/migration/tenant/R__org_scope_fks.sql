-- P3.30 — FKs que a V1_8 não pode criar numa instalação fresca (repetível).
--
-- A V1_8 ordena ANTES da V1_9 (users, P0.13) e da V8_1 (service_protocols,
-- P1.36) na história única do tenant: numa instalação fresca essas tabelas
-- ainda não existem quando a V1_8 roda, e as FKs para elas são puladas lá
-- (guarda da integração P1-C). Esta repetível roda DEPOIS de todas as
-- versionadas e completa as FKs que faltam — o mesmo precedente da
-- R__service_protocol_order_fk.sql da P1.36 e da
-- R__notifications_customer_fk.sql da P3.36. Cada bloco é idempotente pelo
-- nome da constraint: rodar de novo é no-op.

DO $$
BEGIN
    IF EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('users')
              AND c.contype IN ('u', 'p')
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'id'])
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_org_scopes_user')
    THEN
        ALTER TABLE user_org_scopes ADD CONSTRAINT fk_user_org_scopes_user
            FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id);
        ALTER TABLE user_org_scopes ADD CONSTRAINT fk_user_org_scopes_grantor
            FOREIGN KEY (tenant_id, granted_by) REFERENCES users (tenant_id, id);
        ALTER TABLE protocol_org_assignments ADD CONSTRAINT fk_protocol_assignments_actor
            FOREIGN KEY (tenant_id, assigned_by) REFERENCES users (tenant_id, id);
        ALTER TABLE protocol_transfers ADD CONSTRAINT fk_protocol_transfers_actor
            FOREIGN KEY (tenant_id, moved_by) REFERENCES users (tenant_id, id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('service_protocols')
              AND c.contype IN ('u', 'p')
              AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
                  = ARRAY['tenant_id', 'id'])
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_protocol_assignments_protocol')
    THEN
        ALTER TABLE protocol_org_assignments ADD CONSTRAINT fk_protocol_assignments_protocol
            FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols (tenant_id, id);
        ALTER TABLE protocol_transfers ADD CONSTRAINT fk_protocol_transfers_protocol
            FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols (tenant_id, id);
    END IF;
END $$;

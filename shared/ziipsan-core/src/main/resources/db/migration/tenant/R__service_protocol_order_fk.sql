-- Repeatable guard for the protocol -> service-order FK (P1.36).
--
-- Why a repeatable exists for a versioned migration's FK: the tenant
-- history is unique and ordered by version, so V8_1 (protocol) runs
-- BEFORE V9_1 (OS engine) on a fresh install and its conditional FK
-- block must skip (service_orders does not exist yet). V9_1 is already
-- applied and cannot be edited to create the FK. A repeatable migration
-- runs after the versioned ones, so on a fresh install it creates the
-- FK once V9_1 has created service_orders; on an upgrade tenant V8_1
-- already created it and this block is a no-op (duplicate_object guard).
-- Either path ends with the FK in place on a fully migrated tenant, and
-- the 1:N link (one protocol, many orders) is enforced by the database,
-- not only by the application rule (external order without protocol is
-- refused by ServiceOrderService and by the V9_1 origin CHECK).
--
-- In partial classpaths (a module test without the OS engine on the
-- classpath) service_orders does not exist and the block skips, same as
-- the conditional blocks of V9_1 (layer-B fix 42a0fb5). This file must
-- never create any other object: it only closes the ordering gap.
DO $$
BEGIN
    IF to_regclass('service_protocols') IS NOT NULL
        AND to_regclass('service_orders') IS NOT NULL THEN
        BEGIN
            ALTER TABLE service_orders ADD CONSTRAINT fk_orders_protocol
                FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id);
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
    END IF;
END $$;

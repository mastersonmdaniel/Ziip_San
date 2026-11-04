-- ============================================================================
-- P1.3 (camada C) — FKs do cadastro para tabelas de versão maior.
--
-- VERSÃO: V9_3, não V9_2. A V9_2 do tenant está reservada no §3 do plano
-- para a P3.4 (bloqueios de corte); reutilizá-la aqui colidiria com a P3.4
-- no mesmo histórico Flyway do tenant. A V9_3 é a primeira versão livre do
-- tenant após a V9_1 e ordena depois de V5_1 e V9_1, como exige o fresh install.
--
-- POR QUE ESTA MIGRATION EXISTE: o histórico de tenant é ÚNICO e ordenado
-- por versão (Flyway, outOfOrder). A V3_12 (registry, R2) cria colunas que
-- referenciam service_orders (V9_1) e meter_installations (V5_1), que ordenam
-- DEPOIS dela; criar as FKs na V3_12 quebraria o fresh install. As colunas
-- nasceram sem FK na V3_12 (padrão da V3_5 para referências pendentes) e as
-- FKs chegam aqui, na primeira versão livre após a V9_1, que ordena depois
-- das duas tabelas referenciadas. Nenhuma migration existente foi tocada.
-- ============================================================================

-- Correção da integração P1-C (mesmo padrão da V8_1 e do precedente 42a0fb5
-- da camada B): esta migration vive no módulo core, então ela ESTÁ no
-- classpath do teste de provisionamento do platform — mas as tabelas que
-- ela altera são do registry (e de metering), ausentes nesse classpath.
-- Sem guarda de existência, o ALTER quebrava o provisionamento (42P01) e
-- 13/16 testes caíam. Cada bloco agora exige as duas tabelas (alterada e
-- referenciada); na montagem completa todas existem e as FKs aplicam.
DO $$
BEGIN
    IF to_regclass('unit_customers') IS NOT NULL AND to_regclass('service_orders') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ucust_opened_by_so') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_opened_by_so
            FOREIGN KEY (tenant_id, opened_by_service_order_id) REFERENCES service_orders(tenant_id, id);
    END IF;
    IF to_regclass('unit_customers') IS NOT NULL AND to_regclass('service_orders') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ucust_closed_by_so') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT fk_ucust_closed_by_so
            FOREIGN KEY (tenant_id, closed_by_service_order_id) REFERENCES service_orders(tenant_id, id);
    END IF;
    IF to_regclass('unit_due_days') IS NOT NULL AND to_regclass('service_orders') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unit_due_day_so') THEN
        ALTER TABLE unit_due_days ADD CONSTRAINT fk_unit_due_day_so
            FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
    END IF;
    IF to_regclass('cadastral_change_requests') IS NOT NULL AND to_regclass('service_orders') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cadastral_change_so') THEN
        ALTER TABLE cadastral_change_requests ADD CONSTRAINT fk_cadastral_change_so
            FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
    END IF;
    IF to_regclass('unit_alternative_sources') IS NOT NULL AND to_regclass('meter_installations') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_alt_source_meter') THEN
        ALTER TABLE unit_alternative_sources ADD CONSTRAINT fk_alt_source_meter
            FOREIGN KEY (tenant_id, meter_installation_id) REFERENCES meter_installations(tenant_id, id);
    END IF;
    -- NOTA DE CORREÇÃO (camada C): a FK do histórico (fk_ush_service_order)
    -- JÁ EXISTE desde a V9_1 (P1.31), assim como a FK da tarifa social
    -- (fk_social_enroll_order); o bloco abaixo é apenas guarda idempotente
    -- e não cria FK nova. A correção R1 ("com a FK") já estava satisfeita
    -- pela V9_1 para as duas colunas; a P1.3 entrega o que faltava, o
    -- NOT NULL (V3_12 histórico, V4_13 tarifa social). Não criar segunda
    -- FK com outro nome sobre a mesma coluna (duplicata sem valor).
    IF to_regclass('unit_status_history') IS NOT NULL AND to_regclass('service_orders') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ush_service_order') THEN
        ALTER TABLE unit_status_history ADD CONSTRAINT fk_ush_service_order
            FOREIGN KEY (tenant_id, service_order_id) REFERENCES service_orders(tenant_id, id);
    END IF;
END $$;

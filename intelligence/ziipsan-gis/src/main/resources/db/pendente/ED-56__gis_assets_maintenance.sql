-- ============================================================================
-- ED-56 (PENDENTE DE NUMERACAO): ativos, manutencao de rede e inventario
-- regulatorio — GIS-19 (GIS-CADASTRO-TECNICO §GIS-19; ADR-0034;
-- NR 16/2026 da ANA — Res. ANA 302/2026; NR 3/2023; OPE-09, OPE-12,
-- OPE-20, OPE-38; TR-155, TR-1010, SG-1180).
--
-- POR QUE PENDENTE: SQL sem numero confirmado livre na serie tenant
-- do GIS (a serie publicada vai alem da V13_12 e os slots seguintes
-- sao disputados pelas trilhas GIS em integracao). Regra da cascata:
-- NENHUM numero inventado — este arquivo entra como db/pendente/ com
-- o prefixo ED-56__ e recebe o numero da sequencia global na
-- integracao (precedentes: ED-53, ED-54, ED-64, ED-65).
--
-- Depende de: entidade asset da P1.35 (hierarquia de seis niveis —
-- esta migration NAO recria a tabela asset nem o codigo patrimonial:
-- DAD-26, o codigo so existe no ativo da P1.35), V9_1 (service_orders
-- do nucleo de OS da P1.31, consumido pela ED-36) e dos parametros
-- maint.*/inv.*/asset.* do arquivo irmao no core.
--
-- O QUE ESTE SQL FAZ:
--   (1) asset_pm_plan_line: linhas do plano preventivo de ativos nao
--       lineares (OPE-20) — gatilho por tempo OU uso, intervalo DO
--       PLANO DO TENANT (nenhuma periodicidade semeada), plano de
--       trabalho em JSONB (tarefas/materiais/ferramentas/seguranca);
--   (2) asset_valve_exercise: resultado de cada exercicio de valvula
--       (OPE-09) — voltas para fechar, estado encontrado/deixado,
--       fecha ou nao; e a fonte da operabilidade que a GIS-11 le
--       (resposta a P-ED54-05);
--   (3) asset_hydrant_test: teste de hidrante — vazao, pressao e a
--       data do ultimo teste;
--   (4) asset_cctv_inspection + asset_cctv_defect: inspecao por
--       video (OPE-12) com grau de condicao 1..5 por defeito e o
--       padrao declarado (PACP/WRc) gravado por inspecao;
--   (5) asset_risk_assessment: fotografia do risco (probabilidade x
--       consequencia) e do plano de renovacao;
--   (6) asset_reconciliation_run: execucao da conciliacao GIS x
--       patrimonio exportada a ED-32 (GES-14), com as divergencias.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: planos, exercicios, testes,
-- inspecoes e avaliacoes sao do tenant e nascem do uso. Nenhuma
-- vida util, periodicidade ou limiar e gravado aqui como padrao.
--
-- PostGIS real e a execucao deste SQL contra PostgreSQL sao prova
-- da CI (pendencia nomeada P-ED56-03 no relatorio). CORRECAO D1 da
-- revisao da ED-56: HA servidor PostgreSQL 16.15 nesta VM — os dois
-- SQL deste pacote foram APLICADOS de verdade, do zero, em banco
-- descartavel com os stubs declarados (ver RELATORIO-CORRECAO-ED-56):
-- 7/7 tabelas criadas com RLS. O parse com pglast NAO substitui a
-- aplicacao (licao da ED-92).
--
-- CONVENCOES: mensagens sem diacriticos; RLS via register_tenant_table.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (1) Plano preventivo de ativos nao lineares (OPE-20) ------------------------
CREATE TABLE IF NOT EXISTS asset_pm_plan_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    equipment_class_code TEXT NOT NULL,
    trigger_kind TEXT NOT NULL CHECK (trigger_kind IN ('TIME', 'RUNNING_HOURS', 'PUMPED_VOLUME')),
    interval_months INTEGER,
    interval_hours DOUBLE PRECISION,
    interval_volume_m3 DOUBLE PRECISION,
    work_plan JSONB NOT NULL DEFAULT '{}'::jsonb,
    checklist_template_key TEXT,
    CONSTRAINT ck_asset_pm_plan_line_one_interval CHECK (
        (CASE WHEN interval_months IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN interval_hours IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN interval_volume_m3 IS NOT NULL THEN 1 ELSE 0 END) = 1),
    -- Correcao D2 (revisao ED-56): gatilho x intervalo AMARRADOS, como no record Java.
    CONSTRAINT ck_asset_pm_plan_line_trigger_interval CHECK (
        (trigger_kind = 'TIME' AND interval_months IS NOT NULL)
        OR (trigger_kind = 'RUNNING_HOURS' AND interval_hours IS NOT NULL)
        OR (trigger_kind = 'PUMPED_VOLUME' AND interval_volume_m3 IS NOT NULL)),
    CONSTRAINT fk_asset_pm_plan_line_asset
        FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('asset_pm_plan_line');

-- (2) Exercicio de valvulas (OPE-09; alimenta a operabilidade da GIS-11) -----
CREATE TABLE IF NOT EXISTS asset_valve_exercise (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    valve_asset_id UUID NOT NULL,
    exercised_at TIMESTAMP NOT NULL,
    turns_to_close DOUBLE PRECISION NOT NULL,
    found_position TEXT NOT NULL CHECK (found_position IN ('OPEN', 'CLOSED', 'PARTIAL')),
    left_position TEXT NOT NULL CHECK (left_position IN ('OPEN', 'CLOSED', 'PARTIAL')),
    closes_fully BOOLEAN NOT NULL,
    work_order_id UUID,
    CONSTRAINT fk_asset_valve_exercise_asset
        FOREIGN KEY (tenant_id, valve_asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('asset_valve_exercise');

-- (3) Teste de hidrantes (OPE-09) ---------------------------------------------
CREATE TABLE IF NOT EXISTS asset_hydrant_test (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    hydrant_asset_id UUID NOT NULL,
    tested_on DATE NOT NULL,
    flow_rate DOUBLE PRECISION NOT NULL,
    flow_unit TEXT NOT NULL,
    pressure DOUBLE PRECISION NOT NULL,
    pressure_unit TEXT NOT NULL,
    work_order_id UUID,
    CONSTRAINT fk_asset_hydrant_test_asset
        FOREIGN KEY (tenant_id, hydrant_asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('asset_hydrant_test');

-- (4) Inspecao por video de esgoto (OPE-12) -----------------------------------
CREATE TABLE IF NOT EXISTS asset_cctv_inspection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    segment_asset_id UUID NOT NULL,
    standard TEXT NOT NULL CHECK (standard IN ('PACP', 'WRC')),
    inspection_ref TEXT NOT NULL,
    segment_grade INTEGER NOT NULL DEFAULT 0 CHECK (segment_grade BETWEEN 0 AND 5),
    segment_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    work_order_id UUID,
    -- Correcao D1 (revisao ED-56, GRAVE): a FK composta de asset_cctv_defect
    -- referencia (tenant_id, id) — sem esta UNIQUE o SQL NAO APLICAVA em
    -- PostgreSQL real ("there is no unique constraint matching given keys").
    CONSTRAINT uq_asset_cctv_inspection_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_asset_cctv_inspection_asset
        FOREIGN KEY (tenant_id, segment_asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('asset_cctv_inspection');

CREATE TABLE IF NOT EXISTS asset_cctv_defect (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    inspection_id UUID NOT NULL,
    defect_code TEXT NOT NULL,
    condition_grade INTEGER NOT NULL CHECK (condition_grade BETWEEN 1 AND 5),
    distance_meters DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_asset_cctv_defect_inspection
        FOREIGN KEY (tenant_id, inspection_id) REFERENCES asset_cctv_inspection (tenant_id, id)
);
SELECT register_tenant_table('asset_cctv_defect');

-- (5) Risco e plano de renovacao ----------------------------------------------
CREATE TABLE IF NOT EXISTS asset_risk_assessment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    assessed_on DATE NOT NULL,
    condition_grade INTEGER CHECK (condition_grade BETWEEN 1 AND 5),
    recent_breaks INTEGER NOT NULL DEFAULT 0,
    probability INTEGER CHECK (probability BETWEEN 1 AND 5),
    consequence INTEGER NOT NULL CHECK (consequence BETWEEN 1 AND 5),
    score INTEGER,
    band TEXT CHECK (band IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    excluded_reason TEXT,
    CONSTRAINT fk_asset_risk_assessment_asset
        FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('asset_risk_assessment');

-- (6) Conciliacao GIS x patrimonio -> ED-32 (GES-14) ---------------------------
CREATE TABLE IF NOT EXISTS asset_reconciliation_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    exercise INTEGER NOT NULL,
    reference TEXT NOT NULL,
    total_lines INTEGER NOT NULL DEFAULT 0,
    divergent_lines INTEGER NOT NULL DEFAULT 0,
    lines JSONB NOT NULL DEFAULT '[]'::jsonb
);
SELECT register_tenant_table('asset_reconciliation_run');

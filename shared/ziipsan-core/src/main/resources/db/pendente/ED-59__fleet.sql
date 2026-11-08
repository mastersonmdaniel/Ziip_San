-- ============================================================================
-- ED-59 (PENDENTE DE NUMERACAO): frota e equipes de campo (ADR-0028 (i);
-- OPE-28; OPE-33 consumido da ED-36; OPE-14 fronteira da P1.31).
--
-- POR QUE PENDENTE: SQL sem numero confirmado livre na serie tenant do
-- core. Regra da cascata: NENHUM numero inventado — este arquivo entra
-- como db/pendente/ com o prefixo ED-59__ e recebe o numero da
-- sequencia global na integracao (precedentes: ED-36, ED-56, ED-58).
--
-- Depende de: entidade asset da P1.35 com UNIQUE (tenant_id, id) — esta
-- migration NAO recria a tabela asset nem o codigo patrimonial
-- (DAD-26); service_orders da V9_1 (P1.31) com UNIQUE (tenant_id, id)
-- para as FKs de OS; parametros fleet.* do arquivo irmao do core.
--
-- LICAO DA C3 (aplicada): TODA FK composta (tenant_id, X) exige
-- UNIQUE (tenant_id, id) na tabela pai — todas as tabelas deste
-- arquivo declaram CONSTRAINT uq_<tabela>_tenant_id UNIQUE
-- (tenant_id, id), e os pais referenciados (asset, service_orders)
-- tem a UNIQUE pela sua dona (P1.35/P1.31), conferida na aplicacao.
--
-- CONVENCAO DE IDENTIDADE DO VEICULO (correcao R1 da revisao
-- independente, 08/11/2026): TODO o Java identifica o veiculo pelo
-- asset_id da P1.35 (FleetRegistry.Vehicle.assetId,
-- FuelRecord.vehicleAssetId, TrafficFine.vehicleAssetId,
-- CrewAllocation.vehicleAssetId, VehiclePmLine.vehicleAssetId).
-- Portanto as 5 tabelas-filhas (fleet_vehicle_equipment,
-- fleet_fuel_record, fleet_fine, fleet_crew_assignment,
-- fleet_vehicle_pm_plan_line) referenciam fleet_vehicle por
-- (tenant_id, asset_id) — sustentado pela UNIQUE
-- uq_fleet_vehicle_tenant_asset — e NAO pela PK surrogate
-- fleet_vehicle.id. NAO existe convencao fleet_vehicle.id =
-- asset_id: os dois valores sao independentes. As demais FKs
-- compostas (asset, service_orders, fleet_crew,
-- fleet_crew_assignment) seguem por (tenant_id, id), que e a
-- identidade que o Java usa para essas entidades.
--
-- O QUE ESTE SQL FAZ:
--   (1) fleet_vehicle: registro de frota SOBRE o ativo da P1.35
--       (FLEET_ASSET) — placa, licenciamento, situacao, odometro;
--   (2) fleet_vehicle_equipment: equipamentos especiais montados
--       (so os 4 codigos do aceite: HIDROJATO, VACUO, LIMPA_FOSSA,
--       RETROESCAVADEIRA);
--   (3) fleet_driver_license: habilitacao (CNH) do motorista;
--   (4) fleet_fuel_record: abastecimentos (odometro, litros, preco
--       do litro; OS opcional para apropriacao direta);
--   (5) fleet_fine: multas por veiculo e condutor (condutor nulo =
--       autuacao sem identificacao, ver Java);
--   (6) fleet_crew + fleet_crew_member: equipes de campo;
--   (7) fleet_crew_assignment: escala — alocacao datada equipe x
--       veiculo x OS (janela com fim apos o inicio);
--   (8) fleet_pre_trip_checklist: checklist pre-viagem da alocacao
--       (itens em JSONB; o conteudo exigido e do tenant);
--   (9) fleet_vehicle_pm_plan_line: linha do plano preventivo de
--       veiculo — EXTENSAO da rotina da ED-56 (gatilho x intervalo
--       AMARRADOS, como a correcao D2 da ED-56; lead/tolerancia sao
--       os parametros maint.* da ED-56, nao recriados aqui).
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: veiculos, itens de checklist,
-- intervalos e limiares sao do tenant e nascem do uso/cadastro.
--
-- CONVENCOES: mensagens sem diacriticos; RLS via register_tenant_table.
-- ============================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (1) Veiculo de frota sobre o ativo da P1.35 --------------------------------
CREATE TABLE IF NOT EXISTS fleet_vehicle (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    plate TEXT NOT NULL,
    licensing_valid_until DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'IN_MAINTENANCE', 'INACTIVE')),
    odometer_km BIGINT NOT NULL DEFAULT 0 CHECK (odometer_km >= 0),
    CONSTRAINT uq_fleet_vehicle_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_fleet_vehicle_tenant_asset UNIQUE (tenant_id, asset_id),
    CONSTRAINT fk_fleet_vehicle_asset
        FOREIGN KEY (tenant_id, asset_id) REFERENCES asset (tenant_id, id)
);
SELECT register_tenant_table('fleet_vehicle');

-- (2) Equipamentos especiais montados no veiculo ------------------------------
CREATE TABLE IF NOT EXISTS fleet_vehicle_equipment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vehicle_asset_id UUID NOT NULL,
    equipment_code TEXT NOT NULL CHECK (equipment_code IN
        ('HIDROJATO', 'VACUO', 'LIMPA_FOSSA', 'RETROESCAVADEIRA')),
    CONSTRAINT uq_fleet_vehicle_equipment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_fleet_vehicle_equipment_vehicle_code UNIQUE (tenant_id, vehicle_asset_id, equipment_code),
    CONSTRAINT fk_fleet_vehicle_equipment_vehicle
        FOREIGN KEY (tenant_id, vehicle_asset_id) REFERENCES fleet_vehicle (tenant_id, asset_id)
);
SELECT register_tenant_table('fleet_vehicle_equipment');

-- (3) Habilitacao do motorista -------------------------------------------------
CREATE TABLE IF NOT EXISTS fleet_driver_license (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    person_id UUID NOT NULL,
    valid_until DATE NOT NULL,
    category TEXT,
    CONSTRAINT uq_fleet_driver_license_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_fleet_driver_license_tenant_person UNIQUE (tenant_id, person_id)
);
SELECT register_tenant_table('fleet_driver_license');

-- (4) Abastecimentos -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fleet_fuel_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vehicle_asset_id UUID NOT NULL,
    odometer_km BIGINT NOT NULL CHECK (odometer_km >= 0),
    liters NUMERIC(12, 3) NOT NULL CHECK (liters > 0),
    cost_per_liter NUMERIC(12, 4) NOT NULL CHECK (cost_per_liter >= 0),
    fueled_at TIMESTAMP NOT NULL,
    work_order_id UUID,
    CONSTRAINT uq_fleet_fuel_record_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fleet_fuel_record_vehicle
        FOREIGN KEY (tenant_id, vehicle_asset_id) REFERENCES fleet_vehicle (tenant_id, asset_id),
    CONSTRAINT fk_fleet_fuel_record_order
        FOREIGN KEY (tenant_id, work_order_id) REFERENCES service_orders (tenant_id, id)
);
SELECT register_tenant_table('fleet_fuel_record');

-- (5) Multas por veiculo e condutor ----------------------------------------------
CREATE TABLE IF NOT EXISTS fleet_fine (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vehicle_asset_id UUID NOT NULL,
    driver_person_id UUID,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMP NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT uq_fleet_fine_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fleet_fine_vehicle
        FOREIGN KEY (tenant_id, vehicle_asset_id) REFERENCES fleet_vehicle (tenant_id, asset_id)
);
SELECT register_tenant_table('fleet_fine');

-- (6) Equipes de campo -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fleet_crew (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name TEXT NOT NULL,
    driver_person_id UUID NOT NULL,
    CONSTRAINT uq_fleet_crew_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('fleet_crew');

CREATE TABLE IF NOT EXISTS fleet_crew_member (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    crew_id UUID NOT NULL,
    person_id UUID NOT NULL,
    CONSTRAINT uq_fleet_crew_member_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_fleet_crew_member_crew_person UNIQUE (tenant_id, crew_id, person_id),
    CONSTRAINT fk_fleet_crew_member_crew
        FOREIGN KEY (tenant_id, crew_id) REFERENCES fleet_crew (tenant_id, id)
);
SELECT register_tenant_table('fleet_crew_member');

-- (7) Escala: alocacao datada equipe x veiculo x OS ---------------------------------
CREATE TABLE IF NOT EXISTS fleet_crew_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    crew_id UUID NOT NULL,
    vehicle_asset_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    CONSTRAINT ck_fleet_crew_assignment_window CHECK (window_end > window_start),
    CONSTRAINT uq_fleet_crew_assignment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fleet_crew_assignment_crew
        FOREIGN KEY (tenant_id, crew_id) REFERENCES fleet_crew (tenant_id, id),
    CONSTRAINT fk_fleet_crew_assignment_vehicle
        FOREIGN KEY (tenant_id, vehicle_asset_id) REFERENCES fleet_vehicle (tenant_id, asset_id),
    CONSTRAINT fk_fleet_crew_assignment_order
        FOREIGN KEY (tenant_id, work_order_id) REFERENCES service_orders (tenant_id, id)
);
SELECT register_tenant_table('fleet_crew_assignment');

-- (8) Checklist pre-viagem da alocacao ------------------------------------------------
CREATE TABLE IF NOT EXISTS fleet_pre_trip_checklist (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    entries JSONB NOT NULL DEFAULT '[]'::jsonb,
    completed_by UUID NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_fleet_pre_trip_checklist_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fleet_pre_trip_checklist_assignment
        FOREIGN KEY (tenant_id, assignment_id) REFERENCES fleet_crew_assignment (tenant_id, id)
);
SELECT register_tenant_table('fleet_pre_trip_checklist');

-- (9) Plano preventivo de veiculo (extensao da rotina da ED-56) -----------------------
CREATE TABLE IF NOT EXISTS fleet_vehicle_pm_plan_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vehicle_asset_id UUID NOT NULL,
    trigger_kind TEXT NOT NULL CHECK (trigger_kind IN ('TIME', 'ODOMETER_KM')),
    interval_months INTEGER,
    interval_km BIGINT,
    work_plan JSONB NOT NULL DEFAULT '{}'::jsonb,
    checklist_template_key TEXT,
    CONSTRAINT ck_fleet_vehicle_pm_one_interval CHECK (
        (CASE WHEN interval_months IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN interval_km IS NOT NULL THEN 1 ELSE 0 END) = 1),
    CONSTRAINT ck_fleet_vehicle_pm_trigger_interval CHECK (
        (trigger_kind = 'TIME' AND interval_months IS NOT NULL)
        OR (trigger_kind = 'ODOMETER_KM' AND interval_km IS NOT NULL)),
    CONSTRAINT ck_fleet_vehicle_pm_positive CHECK (
        (interval_months IS NULL OR interval_months > 0)
        AND (interval_km IS NULL OR interval_km > 0)),
    CONSTRAINT uq_fleet_vehicle_pm_plan_line_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fleet_vehicle_pm_plan_line_vehicle
        FOREIGN KEY (tenant_id, vehicle_asset_id) REFERENCES fleet_vehicle (tenant_id, asset_id)
);
SELECT register_tenant_table('fleet_vehicle_pm_plan_line');

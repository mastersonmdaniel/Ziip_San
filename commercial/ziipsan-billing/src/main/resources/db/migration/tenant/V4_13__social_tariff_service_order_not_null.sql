-- ============================================================================
-- P1.3 (camada C), correção R1 (gate da camada B) — parte billing.
--
-- A R1 pedia "migration V3_6 com SET NOT NULL nas 2 colunas", mas a V3_6 JÁ
-- EXISTE (registry_territory_fields, P0.16) e não pode ser reutilizada. O
-- NOT NULL é entregue em duas migrations: V3_12 (registry) para
-- unit_status_history.service_order_id e ESTA migration para
-- social_tariff_enrollments.service_order_id. V4_13 é a primeira versão livre
-- do billing após a V4_12 reservada (P2.34, cashback) e ordena após a V4_2,
-- que cria a tabela. Nenhuma migration existente foi tocada.
--
-- A V4_2 criou service_order_id "WITHOUT a foreign key and WITHOUT NOT NULL,
-- exactly" para a P1.31 acrescentar a obrigatoriedade depois; chegou a hora.
-- Linhas legadas com NULL fazem esta migration falhar alto (mesma regra da
-- V3_12): não há OS válida para atribuir retroativamente.
-- ============================================================================

ALTER TABLE social_tariff_enrollments ALTER COLUMN service_order_id SET NOT NULL;

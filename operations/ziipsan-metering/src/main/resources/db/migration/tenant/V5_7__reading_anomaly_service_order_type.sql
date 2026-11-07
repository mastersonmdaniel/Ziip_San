-- Metering Module Migration for Tenant Schema
-- Version: 5.7.0 (P2.5, frente F4 / JOR-10; destino definido na revisao cruzada)
-- Description: reading_anomalies ganha service_order_type_code, o tipo de ordem de
-- servico do catalogo da P1.31 que a ocorrencia de leitura gera.
--
-- ORIGEM: bloco A do trecho notas/F4-DDL.sql. reading_anomalies e tabela do metering
-- (V5_1), por isso o destino e uma migration V5_x propria, e nao a V4_4 do billing
-- (mesmo criterio da correcao B1). A V5_3 NAO e usada: reservada a P2.7.
-- O CHECK foi renomeado de ck_reading_anomalies_service_order_type para
-- chk_reading_anomalies_service_order_type (correcao M9: prefixo chk_ e o padrao de
-- todo o restante do projeto).
--
-- Depende de: V5_1 (reading_anomalies).
--
-- NENHUM DADO E SEMEADO: o catalogo de anormalidades e politica do prestador.

-- ========================================================================
-- OCORRENCIA DE LEITURA: TIPO DE ORDEM DE SERVICO (JOR-10)
-- ========================================================================
-- A entidade ReadingAnomaly so tinha generates_service_order (booleano): dizia QUE
-- gera OS, nao QUAL. A coluna nova guarda a referencia, por codigo, ao catalogo de
-- tipos de OS da P1.31. Sem FK: o catalogo de tipos de OS vive em outro modulo e a
-- V5_1 ja nao cria FK entre modulos alem de consumer_units; a integridade e da
-- camada de aplicacao, como nos demais codigos de catalogo do tenant.
-- A mudanca de situacao (suspensao) e a perda de beneficio NAO ganham coluna: ja
-- existem (suspend_reading_months, suspend_after_occurrences, loses_social_tariff)
-- e a regra pura as deriva.

ALTER TABLE reading_anomalies
    ADD COLUMN IF NOT EXISTS service_order_type_code VARCHAR(60);

-- Gerar OS sem tipo e linha de catalogo incompleta: o banco repete a trava
-- que a regra pura (ReadingOccurrence) aplica na construcao.
ALTER TABLE reading_anomalies
    ADD CONSTRAINT chk_reading_anomalies_service_order_type
    CHECK (generates_service_order = (service_order_type_code IS NOT NULL
                                      AND length(trim(service_order_type_code)) > 0));

-- Platform Migration for Tenant Schema
-- Version: 1.11.0
-- Description: P1.12 — tipo EXPORT em personal_data_access_log.
--
-- A regra única de exportação de listas (P1.12) registra cada exportação em
-- personal_data_access_log, com tipo próprio: sem ele, exportação e leitura
-- de ficha se confundiriam no mesmo tipo PERSONAL_READ. O CHECK original da
-- V1_6 não tem nome explícito, então o PostgreSQL o criou como
-- personal_data_access_log_access_type_check; recria-se o CHECK com o novo
-- valor. Nenhuma linha existente muda de tipo.
ALTER TABLE IF EXISTS personal_data_access_log
    DROP CONSTRAINT IF EXISTS personal_data_access_log_access_type_check;
ALTER TABLE IF EXISTS personal_data_access_log
    ADD CONSTRAINT personal_data_access_log_access_type_check
    CHECK (access_type IN ('DOCUMENT_SEARCH', 'PERSONAL_READ', 'DOCUMENT_REVEAL', 'EXPORT'));

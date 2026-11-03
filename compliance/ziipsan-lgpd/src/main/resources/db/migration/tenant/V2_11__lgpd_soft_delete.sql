-- P1.15 — Baixos de dado pessoal (17:B5, 17:B6): registros excluídos invisíveis
-- às leituras do módulo lgpd.
--
-- As quatro tabelas do lgpd (V2_5) ganham a marca de exclusão lógica
-- (deleted_at), no mesmo padrão já usado pelo cadastro (customers e
-- consumer_units): excluir não apaga a linha — a prova do consentimento precisa
-- sobreviver (LGPD art. 8, §5) —, mas nenhuma leitura do módulo devolve mais a
-- linha excluída (repositórios passam a usar variantes ...AndDeletedAtIsNull).
-- Sem índice novo: as tabelas do lgpd são pequenas e as leituras já chegam por
-- (tenant_id, subject_id) ou (tenant_id, status); um índice só para deleted_at
-- não pagaria a escrita. O registro da exclusão em si (quem excluiu, quando,
-- por quê) fica para o canal do titular da P3.40, que define o fluxo; aqui a
-- migration só cria a coluna que o fluxo vai preencher.
--
-- VERSÃO: V2_11. Conferência de dono antes da escolha (tabela §3 do plano +
-- arquivos em db/migration/tenant): V2_1–V2_10 estão todas ocupadas (quality,
-- losses, gis, mtr, lgpd — V2_6 é do mtr, não do lgpd); V1_7/V1_8 estão
-- reservadas no §3 (P1.18, P3.30) e as demais V1_x ocupadas ou reservadas; a
-- V23_1 é da P3.40 (registro de operações de tratamento) e NÃO é desta tarefa.
-- V2_11 é, portanto, a versão livre mais baixa da história de tenant — e a
-- primeira livre depois da faixa V2_x existente. Nada nesta migration depende
-- da V23_1: ela só acrescenta coluna às tabelas da V2_5.

ALTER TABLE lgpd_consent
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE lgpd_data_subject_request
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE lgpd_dpia
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE lgpd_retention_schedule
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN lgpd_consent.deleted_at IS
    'P1.15: exclusão lógica; linha excluída não aparece em nenhuma leitura do módulo lgpd.';
COMMENT ON COLUMN lgpd_data_subject_request.deleted_at IS
    'P1.15: exclusão lógica; linha excluída não aparece em nenhuma leitura do módulo lgpd.';
COMMENT ON COLUMN lgpd_dpia.deleted_at IS
    'P1.15: exclusão lógica; linha excluída não aparece em nenhuma leitura do módulo lgpd.';
COMMENT ON COLUMN lgpd_retention_schedule.deleted_at IS
    'P1.15: exclusão lógica; linha excluída não aparece em nenhuma leitura do módulo lgpd.';

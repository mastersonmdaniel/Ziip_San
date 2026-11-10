-- V103 - P-R2G1-1 (decisao do dono, item 42 do DECISOES-DO-DONO.md,
-- 09/11/2026): persiste a chave de remessa do debito legado (residual
-- B2-4) em coluna propria de `legacy_debit_entries`, com UNIQUE sobre
-- (tenant_id, remittance_key). Modulo: commercial/ziipsan-collection
-- (schema do tenant). Depende de V102__legacy_debit_febraban150.sql
-- (cria `legacy_debit_entries`).
--
-- DESENHO EXPAND-CONTRACT (decisao do dono, item 46 do
-- DECISOES-DO-DONO.md, 09/11/2026 03:34 -03, que resolve a pendencia
-- P-V103-01): esta migration e o tempo EXPAND. A coluna nasce
-- ANULAVEL (sem a obrigatoriedade fisica nesta migration) e o indice
-- unico ja nasce com ela. No PostgreSQL, o UNIQUE sobre
-- (tenant_id, remittance_key) ignora valores nulos: linhas ainda sem
-- chave nao colidem entre si, logo nenhum ambiente quebra em nenhuma
-- ordem de aplicacao do esquema durante a janela expand. A
-- obrigatoriedade fisica (NOT NULL) fica DIFERIDA para a migration
-- pendente V104 (db/pendente/V104__legacy_debit_remittance_key_not_null.sql),
-- que NAO faz parte deste lote e so pousa COM o pacote do adaptador
-- JPA, que grava a chave em cada gravacao nova. Ao nivel da
-- aplicacao o contrato continua obrigatorio desde ja: a entidade
-- declara a coluna com nullable = false e exige a chave no
-- construtor de aplicacao (ver LegacyDebitEntryEntity).
--
-- A chave e a MESMA derivada em aplicacao por
-- LegacyDebitService.remittanceKey(tenantId, enrollmentId, amount, debitDate)
--   = tenantId + "|" + enrollmentId + "|"
--     + amount.stripTrailingZeros().toPlainString() + "|" + debitDate.
-- A formula NAO muda: esta migration apenas a reproduz em SQL para o
-- backfill das linhas legadas e passa a garanti-la no banco.
--
-- Comprimento maximo da chave (derivacao completa no RELATORIO-V103):
--   tenantId em texto (UUID) = 36; separadores "|" = 3 x 1;
--   enrollment_id VARCHAR(40) = 40 no maximo;
--   amount NUMERIC(14,2) apos stripTrailingZeros: no maximo 15
--     caracteres ("999999999999.99");
--   debit_date em ISO AAAA-MM-DD (LocalDate.toString) = 10.
--   Total maximo = 36 + 1 + 40 + 1 + 15 + 1 + 10 = 104 -> VARCHAR(104).
--
-- Expressao do backfill (equivalente a stripTrailingZeros().toPlainString()
-- para NUMERIC(14,2)): amount::text preserva a escala 2 e nunca usa
-- notacao cientifica; rtrim(..., '0') remove os zeros fracionarios
-- finais (para no '.' quando o valor e inteiro, sem tocar nos zeros do
-- inteiro) e rtrim(..., '.') remove o ponto orfao resultante:
--   100.00 -> "100", 100.50 -> "100.5", 100.55 -> "100.55",
--   100.10 -> "100.1", 0.00 -> "0", 0.10 -> "0.1".
-- A data usa to_char(debit_date, 'YYYY-MM-DD'), o ISO de
-- LocalDate.toString().
--
-- Linhas legadas DUPLICADAS (mesma chave derivada no mesmo tenant):
-- esta migration NAO apaga nem funde nenhuma linha. O bloco DO abaixo
-- verifica os duplicados ANTES do indice unico e levanta excecao com a
-- contagem, abortando a migration (no Flyway sobre PostgreSQL a
-- migration e transacional: nada fica aplicado). A guarda e escopada
-- a valores PREENCHIDOS (remittance_key com valor): sem o escopo, o
-- agrupamento juntaria todas as linhas ainda sem chave de um tenant
-- num unico grupo de nulos e abortaria por duplicado ficticio; com o
-- escopo, so duplicados reais entre chaves preenchidas abortam, que
-- e o que a decisao do item 46 manda. A resolucao dos duplicados
-- reais e a pendencia nomeada P-V103-02 [A DEFINIR] do
-- RELATORIO-V103.
--
-- NENHUM DADO E SEMEADO e nenhuma linha e removida ou fundida: so
-- ADD COLUMN anulavel, UPDATE de backfill da propria coluna, guarda
-- de duplicados e CREATE UNIQUE INDEX, nesta ordem.

ALTER TABLE legacy_debit_entries ADD COLUMN IF NOT EXISTS remittance_key VARCHAR(104);

UPDATE legacy_debit_entries
   SET remittance_key = tenant_id::text || '|' || enrollment_id || '|'
       || rtrim(rtrim(amount::text, '0'), '.') || '|'
       || to_char(debit_date, 'YYYY-MM-DD')
 WHERE remittance_key IS NULL;

DO $$
DECLARE
    duplicate_groups bigint;
    duplicate_rows bigint;
BEGIN
    SELECT COUNT(*), COALESCE(SUM(cnt), 0)
      INTO duplicate_groups, duplicate_rows
      FROM (SELECT COUNT(*) AS cnt
              FROM legacy_debit_entries
             WHERE remittance_key IS NOT NULL
             GROUP BY tenant_id, remittance_key
            HAVING COUNT(*) > 1) d;
    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION 'V103 abortada: % grupo(s) de chave de remessa duplicada em legacy_debit_entries (% linhas) - resolver os duplicados antes de reaplicar (pendencia P-V103-02)',
            duplicate_groups, duplicate_rows;
    END IF;
END $$;

CREATE UNIQUE INDEX idx_legacy_entry_remittance_key ON legacy_debit_entries(tenant_id, remittance_key);

-- ED-27 - Debito em conta: integracao Pix Automatico (P4.5) + legado FEBRABAN 150 (P3.24).
-- SEM NUMERO Flyway: a numeracao e fixada na integracao (precedente P4.1/ED-30/ED-31).
-- A migration do legado em si e a V6_4 da P3.24 (tabelas do FEBRABAN 150) - NAO duplicada aqui.
-- Este SQL cobre so o que e proprio da ED-27: idempotencia do processamento
-- de retorno legado. O consentimento da conversao NAO tem tabela propria
-- aqui: o store CANONICO e `legacy_debit_conversion_consents` (V6_4 da
-- P3.24) - a tabela `debit_conversion_consent`, segundo store sem
-- sincronizacao, foi REMOVIDA por este pacote (B2-2 da rodada 2 da
-- auditoria continua; adaptador: CanonicalConversionConsentAdapter).
-- Nenhum valor de parametro e semeado: pay.legacy_debit_enabled (classe d)
-- e cadastrado por tenant na implantacao; a data de desligamento NAO e
-- coluna digitada - e deduzida em BcbDebitRegimeRules (P3.24, fonte unica)
-- do enquadramento do convenio; DebitRegulatoryCalendar (ED-27) apenas
-- delega a ela (correcao D3).
-- Idempotencia do retorno (correcoes D2/D3): legacy_return_processing guarda
-- DOIS tipos de chave na coluna return_key, com a MESMA PK (tenant, chave):
-- (a) a chave canonica POR DEBITO 'DEBIT:<identificador>', marcada logo apos
-- a baixa de cada linha (semantica alinhada a P3.24, onde a baixa e por
-- debito - assim o mesmo debito vindo em outro arquivo nao baixa 2x); e
-- (b) a chave do arquivo (NSA+hash), marcada so no fim do processamento
-- completo, como atalho de repeticao.

CREATE TABLE IF NOT EXISTS legacy_return_processing (
    tenant_id    uuid        NOT NULL,
    return_key   text        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    settled_count int        NOT NULL DEFAULT 0 CHECK (settled_count >= 0),
    PRIMARY KEY (tenant_id, return_key)
);

-- Registrar a tabela no mecanismo de tenant da plataforma na integracao
-- (B2-6 da rodada 2: o registro estava COMENTADO - nova instancia do padrao G2):
SELECT register_tenant_table('legacy_return_processing');

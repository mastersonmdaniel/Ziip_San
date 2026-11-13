-- V112 — Convergência canónica dos dois modelos de `payments` (decisão 75 do dono,
-- 12/11/2026, §51 do livro + adendos: PD-1..PD-6 todas na Opção A).
--
-- PD-1: nasce um modelo canónico único que funde `Payment` (FEBRABAN, V6_1) e
--   `PaymentEntity` (P3.1, V6_2); as duas entidades são retiradas no Java deste lote.
-- PD-2: vocabulário de `status` unificado ALARGADO = os 15 valores FEBRABAN (CHECK vivo
--   da V6_1) + os 6 valores P3.1 (CHECK pretendido da V6_2). A intersecção é vazia
--   (DIAGNOSTICO-P-D72-V151-01 §2.2); nenhum dos dois vocabulários é descartado.
--   Comprimento: VARCHAR(30) MANTIDO — o literal mais comprido do vocabulário unificado
--   tem 25 caracteres (`CREDIT_RECOVERY_DUPLICATE`); VARCHAR(20) rejeitá-lo-ia (e também
--   `DOC_PROCESSING_ERROR`, 20, ficaria no limite). Default 'RECEBIDO' (o da P3.1:
--   V6_2:69-70 e `PaymentEntity`, já que o CHECK vivo da V6_1 não tinha default).
-- PD-3: as colunas FEBRABAN mantêm-se no modelo canónico (`amount`, `agreement_id`,
--   `return_file_id`, `pending_excess`, `excess_amount`, `credited_on`, `previous_status`,
--   `identified`, `document_type`, `document_id`, `adjustment_*`, `classified_at`,
--   `idempotency_key`); a tradução para o vocabulário FEBRABAN exposto faz-se SÓ na
--   fronteira da API (`PublicApiService`); o contrato `/v1/payments` fica preservado.
-- PD-4: as obrigações NOT NULL sem default da V6_1 passam a CHECKs condicionais por
--   canal: o regime integral (convénio + valor + identificado + chave de idempotência)
--   vale para os canais mediados por retorno/convenio bancário (BOLETO e
--   DEBITO_AUTOMATICO — o débito automático corre pelo retorno FEBRABAN 150, V102);
--   PIX, CARTAO e BALCAO ficam com regime próprio (nenhuma destas quatro colunas é
--   exigida). A função da `idempotency_key` no caminho de retorno fica preservada:
--   o índice único `idx_payments_idempotency` mantém-se.
-- PD-5: derivação automática integral das linhas FEBRABAN existentes (ver passos 3–4).
-- PD-6: lote atómico único: modelo + CHECK + aperto das 5 colunas P3.1 + views +
--   fronteira da API. Lote próprio, SEPARADO do pacote V104.
--
-- Número: V112. A maior versionada PUBLICADA na main viva é a V110 (cura Ed46,
-- `shared/ziipsan-core/.../V110__cutoff_compensation_formula_width.sql`); a V111 está
-- reservada para o registo versionado futuro da renomeação do item-05 (adjudicação
-- R40-3-02) e NÃO é usada aqui. Re-verificar o número contra a main viva no acto do
-- pouso. A lista de publicadas (`published-tenant-migrations.txt`) NÃO se altera
-- neste lote (precedente da cura 42501: a lista actualiza-se na integração).
--
-- Pré-condição PD-5 (P-R27-A-02, RELATORIO-P-R27-A-02.md deste pacote): 0 linhas
-- FEBRABAN em 12 bases alcançáveis (5 instâncias vivas) nesta VM; só 5 linhas em
-- `payments` em toda a VM, todas sintéticas P3.1; as duas tabelas híbridas completas
-- estão VAZIAS. A derivação não tem objecto vivo nesta VM — implementa-se na mesma
-- (para bases fora da VM) e prova-se com dados semeados (ver o teste de convergência
-- deste lote). Limites herdados: 13 clusters parados NÃO consultados (conteúdo
-- desconhecido); bases fora desta VM inalcançáveis — a P-R27-A-02 continua em aberto
-- para elas.
--
-- Decisões de desenho DESTA frente (assunções neutras de menor impacto — NUNCA factos
-- provados; os resíduos [SEM FONTE] estão declarados no RELATORIO-CONVERGENCIA-...):
--   (D1) `payer_public_nature` para linhas existentes = FALSE. É o candidato presente
--     no artefacto (`V6_2:68`, `BOOLEAN NOT NULL DEFAULT FALSE`), não uma decisão
--     provada sobre a natureza do pagador; nenhuma linha FEBRABAN carrega esse dado.
--   (D2) `gross_amount`/`net_amount` derivados de `amount` ("valor pago"): a
--     equivalência `amount` ↔ `gross_amount` NÃO está provada em nenhuma fonte
--     (DIAGNOSTICO §3). A regra usa `amount` como ÚNICA fonte monetária da linha
--     FEBRABAN: `gross_amount = amount`, `segregated_tax_amount = 0`,
--     `withheld_amount = 0`, `net_amount = amount` (logo `net = gross - 0 - 0`).
--     [SEM FONTE] enquanto equivalência; registada como decisão de desenho.
--   (D3) `channel` das linhas FEBRABAN = 'BOLETO' (todas — com ou sem ficheiro de
--     retorno): o formato FEBRABAN 150 é o formato do retorno do boleto; o convénio
--     (`agreement_id`, NOT NULL no regime antigo) é um convénio bancário de
--     cobrança/boleto (`V6_1:20-23`, `document_kind` COLLECTION/BANK_SLIP).
--   (D4) `bill_id` derivado de `document_id` SÓ quando `document_type = 'BILL'`
--     (o token 'BILL' está atestado no código como tipo de documento-conta:
--     `SandboxFixtures.java:50`, `documentType "BILL"`). Outro `document_type`
--     (ex.: 'GUIA') deixa `bill_id` NULL — controlo negativo no teste.
--   (D5) `collection_month` derivado de `paid_on` (dia 1 do mês do recebimento,
--     regra DAD-19 da V6_2) e apertado a NOT NULL nesta passagem: o DDL deixava-o
--     anulável e a entidade P3.1 exigia-o NOT NULL; a derivação preenche-o sempre
--     (`paid_on` é NOT NULL no regime antigo), pelo que o aperto é seguro.
--   (D6) `DEBITO_AUTOMATICO` no regime integral (com BOLETO): é canal mediado por
--     convénio bancário e retorno FEBRABAN 150 (V102); nenhum escritor actual
--     escreve DEBITO_AUTOMATICO em `payments` (o V104 escreve
--     `legacy_debit_entries`), pelo que a decisão não quebra nenhum fluxo vivo.
--   (D7) `identified` das linhas derivadas NÃO é reescrito: mantém o valor gravado
--     na importação (o conceito "não identificado" vive no `status`/`bill_id`).
--   (D8) `payer_document` NÃO é derivado (o detalhe de retorno guarda um
--     `payer_document` por linha, mas a associação pagamento↔detalhe não é 1:1
--     provada; fica NULL nas linhas derivadas).
--
-- Segurança do aperto: no regime antigo, `amount` e `paid_on` são NOT NULL em todas
-- as linhas (V6_1, nunca relaxados antes desta migration), pelo que os passos 3–4
-- preenchem as 5 colunas + `collection_month` em QUALQUER base que satisfaça o
-- esquema antigo. Se uma base tiver uma linha com `gross_amount = 0` (o esquema
-- antigo permitia-o, sem CHECK), o CHECK `gross_amount > 0` da V6_2 pretendida
-- aborta a migration — falha ruidosa, não dado silencioso; nenhuma linha assim
-- existe nas bases consultadas da VM (P-R27-A-02).

-- ========================================================================
-- PASSO 0 — contexto de tenant para a DML desta migration
-- ========================================================================
-- A derivação (passos 3–4) é DML sobre `payments`, tabela com RLS FORÇADA
-- (`register_tenant_table`, V1_2): sem GUC, `current_tenant_id()` é NULL e o
-- UPDATE não veria nenhuma linha — o aperto (passo 5) falharia depois com
-- "contains null values". O placeholder ${tenant_id} é o do TenantMigrator
-- (o checksum do Flyway calcula-se sobre o texto original, antes da
-- substituição); o precedente é a linha 6 das repeatables do billing
-- (R__invoice_required_content, R__legal_message_catalog). O `true` limita
-- a GUC à transacção desta migration.
SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ========================================================================
-- PASSO 1 — `payer_public_nature` (única coluna em falta: DIAGNOSTICO §4)
-- ========================================================================
-- DEFAULT FALSE = candidato presente no artefacto (V6_2:68), decisão de
-- preenchimento desta frente (D1), não facto provado.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payer_public_nature BOOLEAN NOT NULL DEFAULT FALSE;

-- ========================================================================
-- PASSO 2 — Vocabulário unificado de `status` (PD-2)
-- ========================================================================
-- Remove o CHECK FEBRABAN da V6_1 (identificado pela definição, não pelo nome
-- automático, para sobreviver a renomeações locais). Corre ANTES de acrescentar
-- o CHECK novo, que contém os mesmos literais — E ANTES da derivação (passo 3),
-- que escreve os literais P3.1 ('CLASSIFICADO' etc.) que o CHECK antigo rejeita.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%''CLASSIFIED''%'
          AND pg_get_constraintdef(oid) LIKE '%''DOC_NOT_FOUND''%'
    LOOP
        EXECUTE 'ALTER TABLE payments DROP CONSTRAINT ' || quote_ident(c.conname);
    END LOOP;
END $$;

-- ========================================================================
-- PASSO 3 — Derivação PD-5 das linhas FEBRABAN (marcador de formato:
-- `channel IS NULL` — critério (c) da P-R27-A-02; a V15_1 acrescentou `channel`
-- anulável e nenhuma linha FEBRABAN alguma vez o preencheu)
-- ========================================================================
UPDATE payments SET
    channel = 'BOLETO',
    status = CASE status
        WHEN 'CLASSIFIED' THEN 'CLASSIFICADO'
        WHEN 'DUPLICATE' THEN 'DUPLICIDADE'
        WHEN 'DOC_NOT_FOUND' THEN 'NAO_IDENTIFICADO'
        ELSE status
    END,
    gross_amount = amount,
    segregated_tax_amount = 0,
    withheld_amount = 0,
    net_amount = amount,
    bill_id = CASE WHEN document_type = 'BILL' AND document_id IS NOT NULL
        THEN document_id ELSE bill_id END,
    collection_month = DATE_TRUNC('month', paid_on)::date,
    payer_public_nature = FALSE
WHERE channel IS NULL;

-- ========================================================================
-- PASSO 4 — Preenchimento residual das colunas P3.1 em QUALQUER linha que
-- ainda as tenha a NULL (ex.: linha com `channel` já preenchido mas valores
-- parciais). Todas as expressões lêem a linha PRÉ-update (semântica do
-- PostgreSQL), logo `net_amount` usa o `gross_amount` antigo (ou `amount`).
-- ========================================================================
UPDATE payments SET
    gross_amount = COALESCE(gross_amount, amount),
    segregated_tax_amount = COALESCE(segregated_tax_amount, 0),
    withheld_amount = COALESCE(withheld_amount, 0),
    net_amount = COALESCE(net_amount,
        COALESCE(gross_amount, amount)
        - COALESCE(segregated_tax_amount, 0)
        - COALESCE(withheld_amount, 0)),
    collection_month = COALESCE(collection_month, DATE_TRUNC('month', paid_on)::date)
WHERE gross_amount IS NULL
   OR segregated_tax_amount IS NULL
   OR withheld_amount IS NULL
   OR net_amount IS NULL
   OR collection_month IS NULL;

ALTER TABLE payments ADD CONSTRAINT chk_payments_status_unified CHECK (status IN (
    -- Vocabulário FEBRABAN (15, CHECK vivo da V6_1) — preservados verbatim.
    'CLASSIFIED', 'DUPLICATE', 'DOC_NOT_FOUND', 'EXCESS', 'TO_WRITE_DOWN', 'AMOUNT_MISMATCH',
    'OPEN', 'DUPLICATE_REFUNDED', 'TO_ACCOUNT', 'DOC_PRESCRIBED', 'DOC_PARCELED', 'DOC_CANCELED',
    'DOC_PROCESSING_ERROR', 'CREDIT_RECOVERY_CANCELED', 'CREDIT_RECOVERY_DUPLICATE',
    -- Vocabulário P3.1 (6, CHECK pretendido da V6_2).
    'RECEBIDO', 'CLASSIFICADO', 'BAIXADO', 'DUPLICIDADE', 'NAO_IDENTIFICADO', 'TRANSFERIDO'));
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'RECEBIDO';

-- ========================================================================
-- PASSO 5 — Aperto NOT NULL (+ CHECKs da V6_2 pretendida) das 5 colunas P3.1
-- e de `collection_month` (D5). `bill_id` e `payer_document` já estão alinhados
-- (anuláveis nos três sítios) e NÃO entram no aperto.
-- ========================================================================
ALTER TABLE payments ALTER COLUMN channel SET NOT NULL;
ALTER TABLE payments ALTER COLUMN gross_amount SET NOT NULL;
ALTER TABLE payments ALTER COLUMN segregated_tax_amount SET NOT NULL;
ALTER TABLE payments ALTER COLUMN segregated_tax_amount SET DEFAULT 0;
ALTER TABLE payments ALTER COLUMN withheld_amount SET NOT NULL;
ALTER TABLE payments ALTER COLUMN withheld_amount SET DEFAULT 0;
ALTER TABLE payments ALTER COLUMN net_amount SET NOT NULL;
ALTER TABLE payments ALTER COLUMN collection_month SET NOT NULL;

ALTER TABLE payments ADD CONSTRAINT chk_payments_channel
    CHECK (channel IN ('BOLETO', 'PIX', 'CARTAO', 'DEBITO_AUTOMATICO', 'BALCAO'));
ALTER TABLE payments ADD CONSTRAINT chk_payments_gross_amount_positive
    CHECK (gross_amount > 0);
ALTER TABLE payments ADD CONSTRAINT chk_payments_segregated_tax_amount_non_negative
    CHECK (segregated_tax_amount >= 0);
ALTER TABLE payments ADD CONSTRAINT chk_payments_withheld_amount_non_negative
    CHECK (withheld_amount >= 0);
ALTER TABLE payments ADD CONSTRAINT chk_payments_net_amount_non_negative
    CHECK (net_amount >= 0);

-- ========================================================================
-- PASSO 6 — Obrigações PD-4: os 4 NOT NULL sem default da V6_1 passam a
-- CHECKs condicionais por canal. O CHECK antigo de `amount > 0` é removido
-- pela definição com o padrão preciso `(amount > 0)`: não casa com o CHECK
-- novo de `gross_amount > 0` do passo 5 (`(gross_amount > 0)` tem `_` antes
-- de `amount`, não `(`), pelo que este bloco pode correr depois do passo 5.
-- O CHECK é reposto na forma anulável.
-- ========================================================================
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%(amount > 0)%'
    LOOP
        EXECUTE 'ALTER TABLE payments DROP CONSTRAINT ' || quote_ident(c.conname);
    END LOOP;
END $$;

ALTER TABLE payments ALTER COLUMN agreement_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN amount DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN identified DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN idempotency_key DROP NOT NULL;

ALTER TABLE payments ADD CONSTRAINT chk_payments_amount_positive
    CHECK (amount IS NULL OR amount > 0);
-- Regime integral (BOLETO + DEBITO_AUTOMATICO, D6); PIX/CARTAO/BALCAO isentos.
ALTER TABLE payments ADD CONSTRAINT chk_payments_boleto_agreement
    CHECK (channel NOT IN ('BOLETO', 'DEBITO_AUTOMATICO') OR agreement_id IS NOT NULL);
ALTER TABLE payments ADD CONSTRAINT chk_payments_boleto_amount
    CHECK (channel NOT IN ('BOLETO', 'DEBITO_AUTOMATICO') OR amount IS NOT NULL);
ALTER TABLE payments ADD CONSTRAINT chk_payments_boleto_identified
    CHECK (channel NOT IN ('BOLETO', 'DEBITO_AUTOMATICO') OR identified IS NOT NULL);
ALTER TABLE payments ADD CONSTRAINT chk_payments_boleto_idempotency
    CHECK (channel NOT IN ('BOLETO', 'DEBITO_AUTOMATICO') OR idempotency_key IS NOT NULL);
-- O índice único de idempotência (V6_1:142) e a FK do convénio (V6_1:138-139)
-- mantêm-se sem alteração: só mordem quando as colunas vêm preenchidas.

-- ========================================================================
-- PASSO 7 — Views RPT-001..004 (PD-6, atómico)
-- ========================================================================
-- RPT-002 e RPT-003 já vêem as linhas derivadas: filtram os literais P3.1
-- (`NAO_IDENTIFICADO`, `DUPLICIDADE`) para os quais a derivação normalizou os
-- pares `DOC_NOT_FOUND`/`DUPLICATE`. RPT-004 não filtra `status`.
-- RPT-001 (arrecadação diária) filtrava SÓ 'BAIXADO'; `BAIXADO` não tem
-- correspondente FEBRABAN (DIAGNOSTICO §2.2), pelo que nenhuma linha derivada
-- entraria nela — reproduzir-se-ia a cegueira da §2.3 ("RPT-001 devolve zero
-- para toda a arrecadação que tenha entrado pelo fluxo FEBRABAN"). A RPT-001
-- passa a contar também 'CLASSIFICADO': dinheiro recebido E casado com o
-- documento (o estado terminal do fluxo FEBRABAN). Uma linha tem UM só status,
-- logo não há dupla contagem; e o tratamento é simétrico entre proveniências
-- (uma linha P3.1 nativa em CLASSIFICADO conta da mesma forma). Sem outra
-- alteração à definição da view.
CREATE OR REPLACE VIEW rpt_arrecadacao_diaria WITH (security_invoker = true) AS
SELECT p.tenant_id, p.paid_on AS data, p.channel AS canal,
       COUNT(*) AS pagamentos, SUM(p.gross_amount) AS valor_bruto,
       SUM(p.net_amount) AS valor_liquido, SUM(p.withheld_amount) AS valor_retido,
       SUM(p.segregated_tax_amount) AS tributo_segregado
FROM payments p
WHERE p.status IN ('BAIXADO', 'CLASSIFICADO')
GROUP BY p.tenant_id, p.paid_on, p.channel;

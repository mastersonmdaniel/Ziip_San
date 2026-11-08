-- P3.40 — Pré-carga de fábrica do registro de operações de tratamento (LGPD, art. 37).
--
-- Migration REPETÍVEL reservada para a carga de fábrica. NESTA ENTREGA ELA NÃO
-- CONTÉM NENHUM SQL DE CARGA: o único comando executável do arquivo é o
-- set_config do tenant, abaixo. Não há INSERT, não há upsert e não há mecanismo
-- de carga pronto — quem ler o arquivo encontra exatamente isso, e o relatório
-- do encarregado (DpoReport) expõe processingFactoryRecordsLoaded = false para
-- todo tenant enquanto a carga não entrar: a pendência é visível, não escondida.
--
-- *** NENHUMA LINHA É SEMEADA NESTA ENTREGA — E ISSO É DELIBERADO. ***
-- O aceite pede o registro "pré-carregado com as operações de fábrica do pacote
-- sanitation", mas nenhuma fonte disponível enumera essas operações: o plano v3.0 não
-- as lista, o catálogo consent_purposes (V3_12) nasce vazio por tenant (nenhuma
-- finalidade de fábrica é semeada em migration alguma já entregue) e o manifesto da
-- vertical sanitation (P0.47) não estava entre as fontes lidas (a leitura do repo foi
-- interrompida por HTTP 429). Semear operações/finalidades presumidas fabricaria o
-- registro jurídico do tenant — dado inventado, proibido pelo método do projeto.
--
-- Na integração, quando o manifesto da vertical for lido, as linhas de carga
-- entram NESTE arquivo, uma por operação de fábrica, com origin = 'FACTORY',
-- version = 1 e os vínculos de colunas em lgpd_processing_record_columns na
-- mesma carga — é aí, e só aí, que o SQL de carga passa a existir.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- (nenhum SQL de carga nesta entrega — ver o cabeçalho)

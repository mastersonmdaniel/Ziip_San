-- II-8 — Conferência de um bloco (chunk) do lote de faturação.
-- Chamado por ii8-lote-faturacao-release.sh com as variáveis psql
-- :tenant, :limite e :deslocamento. Devolve UMA linha:
--   processados|conformes|divergentes|soma_total_bloco
-- Uma conta é CONFORME quando, recomputada dos seus itens:
--   water_value = soma(net_value dos itens WATER)
--   sewer_value = soma(net_value dos itens SEWER)
--   total_value = water + sewer + debits - credits - taxes  (fórmula da V4_1)
-- e tem pelo menos um item. Qualquer divergência conta como FAILURE do
-- item — o harness Java (BillingBatchReleaseProof) aplica a mesma
-- disciplina sobre o motor real: item falhado conta e é nomeado, o lote
-- continua.
SELECT count(*),
       count(*) FILTER (WHERE conforme),
       count(*) FILTER (WHERE NOT conforme),
       coalesce(sum(total_value), 0)
FROM (
    SELECT b.total_value,
           (b.water_value = coalesce(w.water_items, 0)
            AND b.sewer_value = coalesce(w.sewer_items, 0)
            AND b.total_value = b.water_value + b.sewer_value + b.debits_value
                                - b.credits_value - b.taxes_value
            AND w.itens > 0) AS conforme
    FROM bills b
    LEFT JOIN LATERAL (
        SELECT count(*) AS itens,
               sum(i.net_value) FILTER (WHERE i.service_kind = 'WATER') AS water_items,
               sum(i.net_value) FILTER (WHERE i.service_kind = 'SEWER') AS sewer_items
        FROM bill_items i
        WHERE i.tenant_id = b.tenant_id AND i.bill_id = b.id
    ) w ON true
    WHERE b.tenant_id = :'tenant'
    ORDER BY b.id
    LIMIT :limite OFFSET :deslocamento
) bloco;

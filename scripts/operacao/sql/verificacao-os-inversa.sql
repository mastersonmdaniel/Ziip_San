-- GL.3: prova do efeito da OS inversa que desfez o lote (P3.33, ADR-0039).
--
-- A OS inversa e uma linha em <schema>.service_orders com
-- reverses_order_id = id da OS de origem, do mesmo tenant, encerrada com
-- execucao (status CLOSED_EXECUTED), e o efeito contrario dela aplicado em
-- <schema>.service_order_effects no encerramento (tabelas reais da
-- migration V9_1 do core, schema do tenant). Sem OS inversa encerrada ou
-- sem efeito aplicado, NAO ha prova -- e a etapa do ensaio reprova.
--
-- Uso (psql, pelo ensaio-restauracao-local.sh):
--   psql -v ON_ERROR_STOP=1 -v schema_lote='tenant_alvo' \
--        -v os_origem='33333333-...' -f verificacao-os-inversa.sql
--
-- Saida (uma linha TSV): os_inversas_encerradas <TAB> efeitos_da_inversa
\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
\pset fieldsep '\t'

SELECT
    (SELECT count(*)
       FROM :"schema_lote".service_orders o
      WHERE o.reverses_order_id = :'os_origem'::uuid
        AND o.status = 'CLOSED_EXECUTED'),
    (SELECT count(*)
       FROM :"schema_lote".service_order_effects e
       JOIN :"schema_lote".service_orders o ON o.id = e.order_id
      WHERE o.reverses_order_id = :'os_origem'::uuid
        AND o.status = 'CLOSED_EXECUTED');

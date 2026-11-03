-- P1.4 — Matrícula por sequência (plano v3.0, linha 628).
--
-- DESVIO DE VERSÃO (registrado, não silencioso): o plano reserva a V3_5 para esta
-- tarefa, mas a V3_5 já está ocupada pelos campos estendidos da P0.16
-- (V3_5__registry_unit_extended_fields.sql); a V3_8 está reservada para a P1.41 e
-- a V3_9 foi usada pela P0.22 (índice trigram). A próxima versão livre é a V3_10,
-- usada aqui. Nenhuma migration existente foi renomeada, reutilizada ou reordenada.
--
-- O QUE MUDA (só o tenant; um objeto por schema de tenant)
--   * Sequência registry_matricula_seq no schema do tenant, no lugar do
--     MAX(CAST(matricula AS bigint)) com trava consultiva de ConsumerUnitService.
--     nextval nunca bloqueia: dois lotes paralelos de inclusão não esperam um pelo
--     outro (a trava consultiva serializava a geração) e nunca recebem o mesmo
--     valor; o índice único (tenant_id, matricula) continua sendo a garantia final.
--   * Semeadura: a sequência começa no maior valor numérico já gravado. Tenants
--     novos (sem unidades) começam em 1. Tenants existentes, migrados agora, não
--     regredem para valores já usados: o setval usa o mesmo predicado do código
--     antigo (só dígitos, até 18 posições, para caber em bigint). Detalhe do PG:
--     setval(seq, 0, true) é inválido (MINVALUE padrão é 1), por isso o caso vazio
--     usa setval(seq, 1, false) — o próximo nextval devolve 1 — e o caso com dados
--     usa setval(seq, max, true) — o próximo nextval devolve max + 1.
--   * Ressalva honesta: sequência não é contígua sob rollback — um nextval
--     consumido por transação desfeita não é reutilizado. "Sem lacuna" do aceite
--     vale para lotes confirmados; um lote desfeito pode deixar um salto.
--   * GRANT explícito para ziipsan_app (os default privileges do provisionamento
--     já concedem USAGE, SELECT em sequências criadas pelo migrator; o GRANT aqui
--     deixa a permissão visível na própria migration).
--
-- Nenhuma coluna muda de tipo e nenhuma matrícula existente é reescrita.

CREATE SEQUENCE IF NOT EXISTS registry_matricula_seq AS bigint;

SELECT CASE
    WHEN COALESCE(m, 0) > 0 THEN setval('registry_matricula_seq', m, true)
    ELSE setval('registry_matricula_seq', 1, false)
END
FROM (SELECT MAX(CAST(matricula AS bigint)) AS m FROM consumer_units
       WHERE matricula ~ '^[0-9]+$' AND length(matricula) <= 18) x;

GRANT USAGE, SELECT ON SEQUENCE registry_matricula_seq TO ziipsan_app;

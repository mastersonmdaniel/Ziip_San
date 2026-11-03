-- P0.22 — Busca por trecho do nome com índice GIN trigram (plano v3.0, seção P0).
--
-- DESVIO DE VERSÃO (registrado, não silencioso): o plano chama esta migration de
-- V3_4, mas a V3_4 já está ocupada pela modelagem de cliente da P0.16
-- (V3_4__customer_p016_fields.sql) e o mapa de migrations do plano reserva a V3_8
-- para a P1.41 (situações do titular). Reutilizar qualquer uma das duas criaria
-- colisão futura no Flyway. A próxima versão livre SEM reserva no plano é a V3_9,
-- usada aqui. Nenhuma migration existente foi renomeada, reutilizada ou reordenada.
--
-- O QUE MUDA (só customers, a busca livre por nome do cadastro)
--   * Índice GIN trigram sobre unaccent(lower(name)): a busca "contém" do
--     CustomerService passa a dobrar acentos dos dois lados (Java normaliza o texto
--     digitado; a consulta usa immutable_unaccent(lower(name))), então "joao" casa
--     "João" e o plano usa este índice em vez de varrer o tenant (o comentário do
--     CustomerService dizia explicitamente que não havia índice trigram).
--   * O índice antigo idx_customers_name (tenant_id, lower(name)) FICA: ele serve
--     ao startsWith exato e à ordenação; o trigram serve ao "contém" com dobra de
--     acento. Um índice GIN não carrega tenant_id (não há opclass GIN padrão para
--     UUID); o filtro por tenant continua na consulta e o planner combina os dois
--     índices quando vale a pena.
--
-- POR QUE UMA FUNÇÃO WRAPPER: unaccent() não é IMMUTABLE (o dicionário pode
-- mudar), e expressão de índice exige função IMMUTABLE. immutable_unaccent é um
-- wrapper SQL IMMUTABLE que fixa o dicionário padrão ('unaccent') e o search_path
-- (public, onde o init instala a extensão). Sem o wrapper, o CREATE INDEX falha
-- com "functions in index expression must be marked IMMUTABLE".
--
-- POR QUE A FUNÇÃO NÃO NASCE AQUI (e sim no init): só superusuário pode criar
-- função LEAKPROOF, e a role de migração não é. A função vive em public,
-- criada pelo init de segurança como ziipsan_owner. Esta migration só cria o
-- índice e aborta com mensagem clara se a função não existir ou não for
-- LEAKPROOF (banco sem init não migra silenciosamente).
--
-- LIMITAÇÃO DE PLATAFORMA, LEIA ANTES DE "OTIMIZAR" (reproduzido em 2026-11-03,
-- PG 17.11): sob RLS FORÇADA, o PostgreSQL NÃO USA este índice para LIKE/ILIKE
-- — os operadores textlike (~~) e texticlike (~~*) NÃO são LEAKPROOF, e o
-- planner exclui caminhos de índice cuja condição usa operador não-leakproof
-- quando há quals de RLS. Reproduzido até com índice em coluna pura (sem função
-- nenhuma), a 5.000 e a 100.000 linhas, como migrator: varredura nos dois casos,
-- mesmo quando o bitmap custaria 60 contra 157 da varredura. Um experimento
-- anterior pareceu provar o contrário, mas rodava como superusuário, que ignora
-- RLS — resultado inválido, descartado. Igualdade btree (texteq, leakproof)
-- continua usável sob RLS; FTS (@@) e similaridade (%) também são não-leakproof,
-- ou seja, não há operador de busca por trecho usável em índice sob RLS forçada
-- no PG 17. O índice fica: ele é válido e usado sem RLS, e o teste
-- TrigramSearchIT trava os dois comportamentos. A busca em produção custa uma
-- varredura por tenant (~15 ms a 5k linhas com dobra de acento); se isso é
-- aceitável em tenants grandes é decisão do dono (P1.14/P4.6), não algo que se
-- "corrija" aqui sem quebrar o modelo de RLS.
--
-- PRÉ-REQUISITOS: extensão unaccent e função public.immutable_unaccent, ambas
-- criadas pelo init de segurança (infra/postgres/init/sql/security.sql) como
-- superusuário. A linha de extensão abaixo é IF NOT EXISTS no mesmo padrão da
-- core V1 (a role de migração não pode criar extensão, mas o IF NOT EXISTS
-- sobre extensão já existente é no-op, como já acontece com pg_trgm na core V1).
--
-- DADO: nenhum. Só DDL + índice, sem semente e sem dado sintético.

CREATE EXTENSION IF NOT EXISTS unaccent;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'immutable_unaccent' AND p.proleakproof
    ) THEN
        RAISE EXCEPTION 'P0.22: public.immutable_unaccent LEAKPROOF ausente — rode o init de segurança (infra/postgres/init/sql/security.sql) antes de migrar tenants';
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_customers_name_trgm
    ON customers USING gin (immutable_unaccent(lower(name)) gin_trgm_ops);

-- MM-02 — Parametros por municipio: escopo municipal em parameter_versions.
-- SQL PENDENTE (sem numero Flyway — D-MM-06 do MAPA-DE-PACOTES do bloco
-- multimunicipio; a numeracao global atribui-se na integracao, com a lista
-- de publicadas actualizada no mesmo pouso). Nenhuma migration publicada
-- e tocada: este ficheiro e aditivo sobre a V1_5__tenant_parameters.sql.
--
-- Desenho (decisao item 45; ANALISE-MULTIMUNICIPIO.md §2/§3(a).2/§6):
--   * municipality_ibge NULL = escopo "empresa" (o comportamento vigente).
--     As linhas existentes ja tem NULL por construcao da coluna nova:
--     BACKFILL DE DADOS DESNECESSARIO E NAO EXECUTADO — declarar aqui e
--     a totalidade do "backfill" deste pacote (D-MM-05: parametros
--     vigentes = escopo empresa sem reescrita de dados).
--   * Tipo da coluna: VARCHAR(7) com a mesma CHECK das tabelas vigentes —
--     localities (registry V3_1) e regulatory_profiles (platform V1_13):
--     municipality_ibge VARCHAR(7), IBGE de 7 digitos ou NULL.
--
-- PONTO DELICADO — a EXCLUDE vigente e os NULL (risco nomeado no estudo §6):
--   A EXCLUDE vigente (V1_5) e:
--     EXCLUDE USING gist (tenant_id WITH =, definition_key WITH =,
--                         daterange(valid_from, valid_to, '[]') WITH &&)
--   Acrescentar "municipality_ibge WITH =" ingenuamente QUEBRARIA a
--   proteccao em silencio: em PostgreSQL, NULL nunca e igual a NULL num
--   elemento de EXCLUDE, logo duas versoes de escopo-empresa (NULL)
--   sobrepostas deixariam de colidir.
--   Mecanismo adoptado — o PRECEDENTE PROVADO da propria base, V1_13
--   (regulatory_profiles, P2.31), que resolveu exactamente este problema:
--   o elemento de escopo entra como COALESCE(municipality_ibge, '')
--   WITH = — o NULL da empresa normaliza para '' e volta a colidir
--   consigo mesmo; cada IBGE colide so consigo; empresa ('') e qualquer
--   municipio coexistem. A semantica temporal e os demais elementos da
--   EXCLUDE vigente transcrevem-se EXACTAMENTE — so entra o elemento
--   de escopo, na mesma posicao relativa do precedente (antes do range).
--
-- Depende de: V1_5 aplicada (tabela e constraint vigentes) e btree_gist
-- (init do banco, como a V1_5 ja depende). Idempotente no padrao da
-- V1_13 (ADD COLUMN IF NOT EXISTS; DROP CONSTRAINT IF EXISTS + ADD).

ALTER TABLE parameter_versions ADD COLUMN IF NOT EXISTS municipality_ibge VARCHAR(7)
    CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');

ALTER TABLE parameter_versions DROP CONSTRAINT IF EXISTS excl_parameter_versions_no_overlap;
ALTER TABLE parameter_versions ADD CONSTRAINT excl_parameter_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =,
                        definition_key WITH =,
                        COALESCE(municipality_ibge, '') WITH =,
                        daterange(valid_from, valid_to, '[]') WITH &&);

CREATE INDEX IF NOT EXISTS idx_parameter_versions_scope_from
    ON parameter_versions(tenant_id, definition_key, municipality_ibge, valid_from);

COMMENT ON COLUMN parameter_versions.municipality_ibge IS
    'Municipio (IBGE) a que a versao se aplica (MM-02): NULL = escopo empresa (vale para o tenant inteiro, comportamento vigente); preenchido = versao municipal, que prevalece sobre a da empresa na resolucao por (tenant, chave, municipio, instante).';

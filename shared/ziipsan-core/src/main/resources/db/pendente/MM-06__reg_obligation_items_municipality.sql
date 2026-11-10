-- MM-06 — Agenda regulatoria ED-98 por municipio: escopo municipal em
-- reg_obligation_items (Bloco Multimunicipio, decisao do dono n. 45;
-- MAPA-DE-PACOTES, pacote MM-06; D-MM-01/05/06).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia core do modulo
-- shared/ziipsan-core e e renomeado para V<numero>__*.sql.
--
-- Aditivo sobre o SQL pendente da ED-98
-- (ED-98__regulatory_obligations_agenda.sql, que este ficheiro NAO
-- edita): coluna nova anulavel + unicidade refeita por escopo.
-- Nenhuma migration publicada e tocada; nenhuma linha existente muda
-- de significado (ver abaixo).
--
-- DESENHO (ANALISE-MULTIMUNICIPIO.md §2/§3(a)/§4 + adendo):
--   * municipality_ibge NULL = escopo "empresa" (o comportamento
--     vigente da ED-98: item unico do tenant). As linhas existentes ja
--     tem NULL por construcao da coluna nova: BACKFILL DE DADOS
--     DESNECESSARIO E NAO EXECUTADO (D-MM-05: o vigente = escopo
--     empresa sem reescrita de dados).
--   * Tipo da coluna: o mesmo dos pacotes MM-01/MM-02 e das tabelas
--     vigentes (localities, registry V3_1; regulatory_profiles,
--     platform V1_13, P2.31): VARCHAR(7) com CHECK de 7 digitos ou
--     NULL — municipality_ibge VARCHAR(7)
--     CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$').
--
-- UNICIDADE VIGENTE (transcricao EXACTA do SQL pendente da ED-98):
--   CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_obligation_item_period
--       ON reg_obligation_items(tenant_id, kind, period_ref);
-- So (tenant, kind, periodo): um item por tipo e periodo no tenant.
--
-- PONTO DELICADO — a unicidade por escopo e os NULL:
--   Recriar o indice como (tenant_id, kind, period_ref,
--   municipality_ibge) ingenuamente QUEBRARIA a proteccao em silencio:
--   em PostgreSQL, NULL nunca e igual a NULL num indice unico, logo
--   dois itens de escopo-empresa (NULL) do mesmo kind/periodo
--   deixariam de colidir.
--   Mecanismo adoptado — o PRECEDENTE PROVADO da propria base:
--   V1_13 (regulatory_profiles, P2.31) e MM-02 (parameter_versions)
--   resolvem exactamente este problema normalizando o escopo com
--   COALESCE(municipality_ibge, '') na chave: o NULL da empresa
--   normaliza para '' e volta a colidir consigo mesmo; cada IBGE
--   colide so consigo; empresa ('') e qualquer municipio coexistem;
--   municipios distintos coexistem. A forma vigente transcreve-se
--   EXACTAMENTE — so entra o elemento de escopo, como expressao final
--   da chave (indices unicos sobre expressoes sao o mecanismo do
--   precedente; o nome do indice mantem-se, para nao renomear o que a
--   ED-98 nomeou).
--
-- CONJUNTO DE KINDS MUNICIPAIS (evidencia no RELATORIO-MM-06): so
-- ESFINGE_BIMONTHLY e SISAGUA tem fonte municipal evidenciada
-- (ANALISE-MULTIMUNICIPIO.md §4: "e-Sfinge/SISAGUA ja nascem de perfis
-- por municipio" — perfis regulatory_profiles da P2.31, por
-- municipality_ibge). Este SQL NAO restringe a coluna por kind: o
-- escopo e mecanismo; quais kinds geram por municipio e regra da
-- geracao (MunicipalObligationKinds, MM-06). Kind sem evidencia de
-- fonte municipal fica FORA da geracao municipal — nunca presumido.
--
-- Depende de: SQL pendente da ED-98 aplicado (tabela e indice
-- vigentes). Idempotente no padrao da V1_13/MM-02 (ADD COLUMN IF NOT
-- EXISTS; DROP INDEX IF EXISTS + CREATE UNIQUE INDEX com a chave
-- refeita).

ALTER TABLE reg_obligation_items ADD COLUMN IF NOT EXISTS municipality_ibge VARCHAR(7)
    CHECK (municipality_ibge IS NULL OR municipality_ibge ~ '^[0-9]{7}$');

DROP INDEX IF EXISTS uq_reg_obligation_item_period;
CREATE UNIQUE INDEX uq_reg_obligation_item_period
    ON reg_obligation_items(tenant_id, kind, period_ref, COALESCE(municipality_ibge, ''));

CREATE INDEX IF NOT EXISTS idx_reg_obligation_items_scope
    ON reg_obligation_items(tenant_id, municipality_ibge, kind, period_ref);

COMMENT ON COLUMN reg_obligation_items.municipality_ibge IS
    'Municipio (IBGE) a que o item da agenda se aplica (MM-06): NULL = escopo empresa (vale para o tenant inteiro, comportamento vigente da ED-98); preenchido = item gerado por municipio, nos kinds com fonte em perfis regulatorios por municipio (P2.31). A unicidade uq_reg_obligation_item_period inclui COALESCE(municipality_ibge, '''') — precedente V1_13/MM-02 — para o escopo-empresa continuar a colidir consigo mesmo.';

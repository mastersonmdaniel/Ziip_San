-- ED-55 — GIS-10 (views qgis_*/qgis_edit_*) e GIS-18 (fila de
-- casamento SG-1349, conciliacao SG-1356 e etapas de implantacao).
--
-- POR QUE EM db/pendente (sem numero Flyway):
--   * O slot reservado pelo plano para a ED-55 e o core V12
--     (platform.gis_db_role), entregue como migration real em
--     shared/ziipsan-core. Para as tabelas/views DO TENANT da ED-55 o
--     plano reserva apenas a familia generica V13_x, sem numero
--     proprio; a serie tenant ja vai ate V13_12 na main e V13_13 no
--     pacote local da P3.27. Nenhum numero foi inventado
--     (precedente ED-67/ED-17/ED-14): a integracao atribui o proximo
--     numero livre ao pousar.
--   * As views dependem das tabelas de feicao da GIS-01/02 (V13_2) e
--     dos pacotes da GIS-04 (V13_9): pousar junto/depois delas.
--
-- CONVENCOES (as mesmas das V13_8/V13_9/V13_11/V13_12): search_path
-- LOCAL fixado no schema do tenant nas funcoes; mensagens sem
-- diacriticos; RLS via register_tenant_table nas tabelas novas.
--
-- GIS-10 (§7.1): o tenant da sessao QGIS e resolvido pela IDENTIDADE
-- DA ROLE (platform.gis_role_tenant(), core V12) — nunca por GUC.
-- As views usam security_invoker = true: quem chama e a role qgis_*,
-- e a RLS da tabela base continua valendo; o filtro explicito pelo
-- tenant da role e defesa em profundidade e e o que faz a mesma view
-- funcionar em schema proprio (tenant_schema) e em schema
-- compartilhado. security_invoker tem um preco obrigatorio: a role
-- precisa de SELECT nas tabelas base sob as views (sem ele, toda
-- leitura/edicao falha com permission denied) — a prescricao de
-- grants completa, com a decisao registrada, esta no fim da secao
-- (2), correcao A1 pos-confirmacao.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) GIS-10 — leitura: views qgis_* (todas as feicoes do proprio
--     tenant, em qualquer ciclo de vida; nada de outro tenant).
--     A spec (§7.1 item 2) NAO pede filtro de ciclo de vida nas views
--     de leitura — quem filtra por ciclo e a publicacao/rede na data
--     (GIS-04/GIS-06); prometer "somente em operacao e PLANNED" aqui
--     seria descrever um filtro que nao existe (correcao D7 da
--     revisao cruzada).
-- ======================================================================

CREATE OR REPLACE VIEW qgis_node
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.catalog_item_id,
       f.attributes, f.schema_version, f.lifecycle_status,
       f.operational_status, f.accuracy_status, f.capture_method,
       f.geom, f.version
  FROM gis_node f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE OR REPLACE VIEW qgis_arc
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.catalog_item_id,
       f.attributes, f.schema_version, f.lifecycle_status,
       f.operational_status, f.accuracy_status, f.capture_method,
       f.node_1_id AS from_node_id, f.node_2_id AS to_node_id,
       f.geom, f.version
  FROM gis_arc f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE OR REPLACE VIEW qgis_structure
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.catalog_item_id,
       f.attributes, f.schema_version, f.lifecycle_status,
       f.operational_status, f.accuracy_status, f.capture_method,
       f.geom, f.geom_point, f.version
  FROM gis_structure f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE OR REPLACE VIEW qgis_connection
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.catalog_item_id,
       f.attributes, f.schema_version, f.lifecycle_status,
       f.operational_status, f.accuracy_status, f.capture_method,
       f.geom, f.version
  FROM gis_connection f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE OR REPLACE VIEW qgis_zone
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.catalog_item_id,
       f.attributes, f.schema_version, f.lifecycle_status,
       f.operational_status, f.accuracy_status, f.capture_method,
       f.geom, f.version
  FROM gis_zone f
 WHERE f.tenant_id = platform.gis_role_tenant();

-- ======================================================================
-- (2) GIS-10 — escrita: views qgis_edit_* com INSTEAD OF.
--     A escrita NUNCA toca a rede em operacao: o gatilho grava um item
--     no pacote QGIS aberto (DRAFT) do usuario dono da role, com o
--     estado proposto; submeter e revisar (por OUTRA pessoa) e o fluxo
--     da GIS-04 (V13_9). A validacao do pacote (gis_check_feature e as
--     guardas da V13_9) acontece na aplicacao do pacote — a view nao
--     contorna nenhuma guarda porque nao escreve em tabela de feicao.
--     A funcao do gatilho e SECURITY DEFINER de uma role sem login
--     (criada pela rotina de implantacao) que so escreve nas tabelas
--     de pacote; aqui a funcao nasce no schema do tenant e a troca do
--     dono e passo declarado da implantacao (ver doc da ED-55).
-- ======================================================================

CREATE OR REPLACE FUNCTION qgis_edit_apply()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_temp
AS $$
DECLARE
    v_tenant UUID := platform.gis_role_tenant();
    v_user   UUID := platform.gis_role_user();
    v_set    UUID;
    v_table  TEXT := TG_ARGV[0];
    v_geom   TEXT;
    v_feature UUID;
BEGIN
    -- search_path para o schema onde a view foi disparada.
    PERFORM set_config('search_path',
        quote_ident(TG_TABLE_SCHEMA) || ', platform, public, pg_temp', true);
    IF v_tenant IS NULL OR v_user IS NULL THEN
        RAISE EXCEPTION 'qgis: role sem mapeamento ativo (core V12)';
    END IF;
    -- A sessao QGIS real nunca seta app.current_tenant, mas a RLS das
    -- tabelas de pacote (register_tenant_table) exige a GUC no WITH
    -- CHECK dos INSERTs abaixo. Quem fixa a GUC aqui e a PROPRIA
    -- funcao SECURITY DEFINER, com o tenant resolvido pela identidade
    -- da role (core V12) — nunca com valor vindo do cliente/sessao
    -- (correcao D9 da revisao cruzada).
    PERFORM set_config('app.current_tenant', v_tenant::text, true);
    IF TG_OP = 'DELETE' THEN
        -- Retirada tambem e proposta de pacote, nunca DELETE na base.
        v_geom := NULL;
    ELSE
        v_geom := ST_AsText(NEW.geom);
    END IF;
    -- Pacote QGIS aberto do usuario: reutiliza o DRAFT ou cria um.
    SELECT id INTO v_set FROM gis_change_set
     WHERE tenant_id = v_tenant AND author_id = v_user
       AND type = 'QGIS' AND channel = 'QGIS' AND status = 'DRAFT'
     ORDER BY created_at LIMIT 1;
    IF v_set IS NULL THEN
        v_set := uuid_generate_v4();
        INSERT INTO gis_change_set (id, tenant_id, type, channel, status,
                author_id, created_by, updated_by)
        VALUES (v_set, v_tenant, 'QGIS', 'QGIS', 'DRAFT',
                v_user, v_user, v_user);
    END IF;
    IF TG_OP = 'INSERT' THEN
        -- feature_id e NOT NULL no item (V13_9): se o QGIS nao enviou
        -- id, o UUID nasce no banco (§7.1 item 4: valores padrao
        -- avaliados no servidor, UUID gerado no banco) — nunca NULL.
        v_feature := COALESCE(NEW.id, uuid_generate_v4());
        INSERT INTO gis_change_set_item (tenant_id, change_set_id,
                operation, feature_table, feature_id, proposed_state,
                proposed_geom_wkt, created_by, updated_by)
        VALUES (v_tenant, v_set, 'INSERT', v_table, v_feature,
                COALESCE(NEW.attributes, '{}'::jsonb), v_geom,
                v_user, v_user);
    ELSIF TG_OP = 'UPDATE' THEN
        -- base_version e a versao que o editor LEU (OLD.version), nao a
        -- que ele devolveu (NEW.version): com NEW, um cliente que
        -- enviasse version alterado enfraqueceria a checagem de
        -- rebase da GIS-04 na aplicacao do pacote (correcao D8).
        INSERT INTO gis_change_set_item (tenant_id, change_set_id,
                operation, feature_table, feature_id, base_version,
                proposed_state, proposed_geom_wkt, created_by, updated_by)
        VALUES (v_tenant, v_set, 'UPDATE', v_table, OLD.id, OLD.version,
                COALESCE(NEW.attributes, '{}'::jsonb), v_geom,
                v_user, v_user);
    ELSE
        INSERT INTO gis_change_set_item (tenant_id, change_set_id,
                operation, feature_table, feature_id, base_version,
                proposed_state, created_by, updated_by)
        VALUES (v_tenant, v_set, 'RETIRE', v_table, OLD.id, OLD.version,
                '{}'::jsonb, v_user, v_user);
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

-- Uma view editavel generica por papel: expoe as colunas que o gatilho
-- consome (id, attributes, geom, version). A geometria de gis_structure
-- pontual entra por geom_point no servico Java/.qgz; a view editavel
-- da estrutura usa geom (poligonal) — limitacao declarada no relatorio.
CREATE OR REPLACE VIEW qgis_edit_node
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.attributes,
       f.geom, f.version
  FROM gis_node f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE TRIGGER qgis_edit_node_io
INSTEAD OF INSERT OR UPDATE OR DELETE ON qgis_edit_node
FOR EACH ROW EXECUTE FUNCTION qgis_edit_apply('gis_node');

CREATE OR REPLACE VIEW qgis_edit_arc
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.attributes,
       f.geom, f.version
  FROM gis_arc f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE TRIGGER qgis_edit_arc_io
INSTEAD OF INSERT OR UPDATE OR DELETE ON qgis_edit_arc
FOR EACH ROW EXECUTE FUNCTION qgis_edit_apply('gis_arc');

CREATE OR REPLACE VIEW qgis_edit_structure
WITH (security_invoker = true) AS
SELECT f.id, f.tenant_id, f.code, f.feature_type_id, f.attributes,
       f.geom, f.version
  FROM gis_structure f
 WHERE f.tenant_id = platform.gis_role_tenant();

CREATE TRIGGER qgis_edit_structure_io
INSTEAD OF INSERT OR UPDATE OR DELETE ON qgis_edit_structure
FOR EACH ROW EXECUTE FUNCTION qgis_edit_apply('gis_structure');

-- Privilegios: NENHUM grant e dado nesta migration. A rotina de
-- implantacao concede, por role qgis_*:
--   * USAGE no schema do tenant;
--   * USAGE no schema platform (correcao R1 pos-segunda-confirmacao:
--     sem ele, a chamada POR NOME platform.gis_role_tenant()/
--     gis_role_user() como a role falha com "permission denied for
--     schema platform" sob a ACL padrao do projeto — o core so
--     concede USAGE em platform a ziipsan_app/ziipsan_central; o
--     runtime das views/gatilho nao precisa, porque as referencias
--     internas sao por OID, mas o teste de identidade chama as
--     funcoes por nome);
--   * SELECT nas qgis_*; SELECT/INSERT/UPDATE/DELETE nas qgis_edit_*
--     (o INSTEAD OF decide);
--   * EXECUTE em platform.gis_role_tenant()/gis_role_user();
--   * USAGE no schema platform e EXECUTE nas MESMAS funcoes tambem
--     para a role DONA da funcao qgis_edit_apply: a funcao e
--     SECURITY DEFINER e chama gis_role_tenant()/gis_role_user()
--     COMO A DONA — sem esses grants, a edicao falha com
--     "permission denied for function gis_role_tenant" ou
--     "permission denied for schema platform" (provado em
--     PostgreSQL real por esta frente de correcao; quando a dona e
--     a propria dona das funcoes do core, o grant e desnecessario
--     porque dono nao precisa de EXECUTE no que e seu);
--   * SELECT — e SOMENTE SELECT — nas tabelas base SOB as views:
--     gis_node, gis_arc, gis_structure, gis_connection, gis_zone.
--
-- DECISAO DE DESENHO (correcao A1 pos-confirmacao, achado GRAVE da
-- revisao de confirmacao): a prescricao anterior ("NADA nas tabelas
-- base") era INCOMPATIVEL com security_invoker = true e foi
-- SUBSTITUIDA por esta. Com security_invoker, o PostgreSQL confere os
-- privilegios das tabelas base COMO O INVOCADOR: sem SELECT na base,
-- a role recebia "permission denied for table gis_node" em TODA
-- leitura e edicao pelas views — o caminho QGIS nascia quebrado
-- (provado em PostgreSQL real pela revisao de confirmacao e por esta
-- frente de correcao). O grant minimo compativel com o desenho e o
-- SELECT nas cinco tabelas sob as views de leitura (as tres sob as
-- editaveis sao subconjunto): ele serve a leitura das views E a
-- selecao das linhas pelas views editaveis; a escrita em si NAO e na
-- base — e do gatilho SECURITY DEFINER sobre
-- gis_change_set/gis_change_set_item (pacote DRAFT do usuario).
-- Por isso a role continua SEM INSERT/UPDATE/DELETE em qualquer
-- tabela base e SEM grant algum em gis_change_set/
-- gis_change_set_item: escrever direto na rede segue impossivel para
-- ela (§7.1 item 2, tabelas base sem escrita, preservado), ela nasce
-- SEM BYPASSRLS, e a barreira de isolamento entre tenants e a RLS
-- (FORCE, chaveada no tenant) somada ao filtro explicito
-- tenant_id = platform.gis_role_tenant() das views — o tenant e
-- resolvido pela identidade da role, nunca pelo payload.
-- Alternativa registrada para o dono do plano (NAO aplicada): remover
-- o security_invoker das views e deixar o filtro explicito como
-- barreira unica, com os privilegios do dono da view — dispensaria o
-- SELECT na base, mas enfraquece a defesa em profundidade (a RLS
-- deixaria de valer para a sessao QGIS) e muda o desenho aprovado;
-- fica como questao aberta, nao como desenho vigente.

-- ======================================================================
-- (2b) GIS-18 — ampliacao do CHECK de formato do lote (V13_11)
--     A V13_11 (GIS-07) nao conhece INP_EPANET/INP_SWMM: sem esta
--     ampliacao, o lote INP da GIS-18 nao pode nem ser INSERIDO em
--     gis_import_batch (correcao D3 da revisao cruzada). O CHECK
--     original e inline na coluna (nome automatico do PostgreSQL
--     gis_import_batch_source_format_check); o bloco abaixo localiza
--     o CHECK da lista de formatos PELO CATALOGO, remove a constraint
--     vigente DE FATO e recria a lista completa (GIS-07 + INP).
--     O CHECK de conversao da V13_11 (ck_gis_import_batch_conversion)
--     ja trata INP no ramo "sem conversao registrada" — nao muda.
--
--     CORRECAO D3a (pos-confirmacao): a primeira versao deste bloco
--     procurava a definicao com LIKE '%source_format IN (%' — forma
--     que o PostgreSQL NUNCA devolve: pg_get_constraintdef/
--     pg_get_expr "desparseiam" o IN como
--     (source_format = ANY (ARRAY['DWG'::text, ...])). O bloco nao
--     localizava nada, a constraint automatica sobrevivia ao lado da
--     nova e o INSERT de INP seguia recusado (provado em PostgreSQL
--     real pela revisao de confirmacao). A localizacao abaixo casa
--     com a forma REAL da expressao no catalogo
--     ('source_format = ANY (ARRAY[') via pg_get_expr(conbin), que e
--     a representacao estavel da lista de formatos: o CHECK de
--     conversao, que tambem menciona source_format, NAO casa (ele e
--     igualdades soltas e um <> ALL, nunca "source_format = ANY
--     (ARRAY["); e uma constraint que ja contenha INP_EPANET nao e
--     removida. Provado em banco real por esta frente: INSERT
--     INP_EPANET/INP_SWMM aceitos, formato fora da lista recusado e
--     UMA unica constraint de lista vigente ao final.
-- ======================================================================

DO $$
DECLARE
    v_con TEXT;
BEGIN
    FOR v_con IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'gis_import_batch'::regclass
           AND contype = 'c'
           AND conname <> 'ck_gis_import_batch_source_format'
           AND pg_get_expr(conbin, conrelid)
               LIKE '%source_format = ANY (ARRAY[%'
           AND pg_get_expr(conbin, conrelid) NOT LIKE '%INP_EPANET%'
    LOOP
        EXECUTE format('ALTER TABLE gis_import_batch DROP CONSTRAINT %I',
                       v_con);
    END LOOP;
END $$;

-- Rede de seguranca pelo nome que o PostgreSQL gera para o CHECK
-- inline de coluna da V13_11 (se o bloco acima ja a removeu, o
-- IF EXISTS torna isto um no-op).
ALTER TABLE gis_import_batch
    DROP CONSTRAINT IF EXISTS gis_import_batch_source_format_check;

ALTER TABLE gis_import_batch
    DROP CONSTRAINT IF EXISTS ck_gis_import_batch_source_format;

ALTER TABLE gis_import_batch
    ADD CONSTRAINT ck_gis_import_batch_source_format
    CHECK (source_format IN
        ('DWG', 'DXF', 'SHP', 'GPKG', 'DGN_V7', 'DGN_V8', 'CSV_GNSS',
         'INP_EPANET', 'INP_SWMM'));

-- ======================================================================
-- (3) GIS-18 — casamento de unidades (SG-1349) com fila de revisao
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_unit_match_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    raw_id UUID,
    -- Chaves do casamento: matricula da unidade consumidora ou o
    -- endereco normalizado do desenho/base. Uma das duas e obrigatoria.
    registration_key TEXT,
    normalized_address TEXT,
    candidate_unit_id UUID,
    -- Conjunto AVALIADO no casamento (ids das unidades candidatas
    -- consideradas, JSON array de textos): a decisao humana sobre
    -- item AMBIGUOUS/PENDING_REVIEW so pode confirmar candidato
    -- DESTE conjunto (correcao D4 da revisao cruzada) — sem ele, o
    -- item ambiguo nascia sem candidato e nunca podia virar MATCHED.
    candidate_options JSONB NOT NULL DEFAULT '[]',
    match_method TEXT CHECK (match_method IN
        ('REGISTRATION', 'ADDRESS_EXACT', 'ADDRESS_PREFIX', 'NONE')),
    match_status TEXT NOT NULL DEFAULT 'PENDING_REVIEW' CHECK (match_status IN
        ('MATCHED', 'PENDING_REVIEW', 'REJECTED', 'AMBIGUOUS')),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_unit_match_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_unit_match_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES gis_import_batch (tenant_id, id),
    CONSTRAINT ck_gis_unit_match_key
        CHECK (registration_key IS NOT NULL OR normalized_address IS NOT NULL),
    -- MATCHED pode nascer automatico (matricula exata ou endereco
    -- exato com candidato unico, metodo registrado na linha); a
    -- decisao humana (decideMatch) preenche decided_by. REJECTED so
    -- existe por decisao humana, por isso exige o decisor.
    CONSTRAINT ck_gis_unit_match_decision
        CHECK (match_status <> 'REJECTED' OR decided_by IS NOT NULL)
);

SELECT register_tenant_table('gis_unit_match_queue');

CREATE INDEX IF NOT EXISTS idx_gis_unit_match_pending
    ON gis_unit_match_queue (tenant_id, batch_id, match_status)
    WHERE match_status IN ('PENDING_REVIEW', 'AMBIGUOUS');

-- ======================================================================
-- (4) GIS-18 — conciliacao base cadastral x desenho (SG-1356)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_reconciliation_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN
        ('ONLY_IN_BASE', 'ONLY_IN_DRAWING', 'GEOMETRY_DIVERGENT',
         'ATTRIBUTE_DIVERGENT', 'AGREED')),
    base_ref TEXT,
    drawing_ref TEXT,
    detail JSONB NOT NULL DEFAULT '{}',
    resolution TEXT CHECK (resolution IN
        ('KEEP_BASE', 'KEEP_DRAWING', 'FIELD_SURVEY', 'IGNORED')),
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_reconciliation_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_reconciliation_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES gis_import_batch (tenant_id, id),
    CONSTRAINT ck_gis_reconciliation_ref
        CHECK (base_ref IS NOT NULL OR drawing_ref IS NOT NULL),
    CONSTRAINT ck_gis_reconciliation_resolution
        CHECK (resolution IS NULL OR resolved_by IS NOT NULL)
);

SELECT register_tenant_table('gis_reconciliation_item');

CREATE INDEX IF NOT EXISTS idx_gis_reconciliation_batch
    ON gis_reconciliation_item (tenant_id, batch_id, outcome);

-- ======================================================================
-- (5) GIS-18 — implantacao em tres etapas (SG-1334) e treinamento
--     (SG-884, SG-885): registro auditavel, sem datas inventadas —
--     quem preenche prazos e a implantacao real; aqui so a maquina de
--     estados e o teto de 60 dias da amostra (conferido no Java e no
--     ensaio; o banco guarda as datas declaradas).
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_rollout_plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    stage TEXT NOT NULL CHECK (stage IN ('STAGE_1', 'STAGE_2', 'STAGE_3')),
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN
        ('PLANNED', 'IN_PROGRESS', 'DONE', 'BLOCKED')),
    is_sample_network BOOLEAN NOT NULL DEFAULT FALSE,
    started_on DATE,
    sample_deadline_on DATE,
    finished_on DATE,
    training_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    training_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_rollout_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_rollout_stage UNIQUE (tenant_id, stage),
    CONSTRAINT ck_gis_rollout_sample_window
        CHECK (NOT is_sample_network OR sample_deadline_on IS NULL
               OR started_on IS NULL
               OR sample_deadline_on <= started_on + 60)
);

SELECT register_tenant_table('gis_rollout_plan');

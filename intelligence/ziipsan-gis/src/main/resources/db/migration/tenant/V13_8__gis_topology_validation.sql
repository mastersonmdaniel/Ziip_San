-- GIS-03 (P2.21) — topologia persistida e validacao por area suja
-- (GIS-CADASTRO-TECNICO §3.4, §5.1, §5.2, §5.3 e §5.5).
--
-- NUMERACAO: a spec reservava a V13_3 para esta entrega, mas V13_3 a V13_7 ja
-- foram consumidas no repo real (redaction, aprovacao de catalogo e fixes da
-- integracao P1c). Precedente do projeto: slot ocupado -> proxima versao livre
-- da serie. Esta e a V13_8, UMA SO migration versionada; os seeds de regras de
-- conectividade do §5.3 vao na repetivel R__gis_factory_catalog.sql.
--
-- O QUE ESTA MIGRATION FAZ:
--   (1) tabelas gis_dirty_area e gis_validation_issue (§3.4/§5.5): a primeira
--       recebe, por gatilho, o envelope de toda gravacao em feicao; a segunda
--       e o destino dos problemas que o worker (frente de servico) grava ao
--       consumir as areas sujas — NENHUM gatilho desta migration escreve nela;
--   (2) gatilho de area suja AFTER INSERT OR UPDATE OR DELETE nas 6 tabelas de
--       feicao (gis_node, gis_arc, gis_connection, gis_structure, gis_zone,
--       gis_poi);
--   (3) snap topologico BEFORE INSERT OR UPDATE OF geom, lifecycle_status
--       em gis_arc e gis_connection (§5.2; lifecycle_status entrou na
--       correcao B2 da revisao, para a obrigatoriedade valer tambem na
--       transicao de estado): ponta perto de no existente cola no no (move
--       o vertice e preenche a chave); ponta sem no cria no JUNCTION; arco
--       em IN_SERVICE ou UNDER_CONSTRUCTION sem no nas duas pontas ao fim
--       do snap e RECUSADO (§5.1);
--   (4) divisao de arco: no novo sobre arco existente (<= tolerancia, sem ser
--       ponta ja ligada) divide o arco em dois, preservando atributos,
--       historico e pacote;
--   (5) arrastar pontas: mover um no move o vertice extremo dos arcos ligados
--       (e a ponta inicial dos ramais ligados pelo tap_node_id);
--   (6) view gis_network_element recriada com a MESMA definicao da V13_2 mais
--       duas colunas NO FIM (node_1_code, node_2_code).
--
-- O QUE ESTA MIGRATION NAO FAZ (de proposito):
--   * nao altera gis_check_feature (V13_2, com a forma vigente da V13_6) nem
--     gis_write_history (forma vigente da V13_7): o snap roda em gatilho
--     proprio e o historico existente cobre os UPDATEs que a divisao e o
--     arrasto fazem;
--   * nao cria tabelas gis_*_history para dirty_area/validation_issue: elas
--     nao sao feicoes (o historico de uma area suja e o seu proprio ciclo
--     PENDING -> PROCESSING -> DONE/FAILED);
--   * nao roda validacao nenhuma do §5.4: quem valida e o worker sobre as
--     areas sujas (frente de servico da mesma tarefa); aqui so se marca a
--     area e se da a tabela de problemas;
--   * nao publica na outbox: a outbox da P1.9 nao vive neste schema -- e a
--     public.outbox unica por banco da V8 do core (particionada, com
--     dedup_key e outbox_ensure_partition), cuja publicacao desenhada e a
--     do PostgresMessageQueue na transacao de quem publica. O gatilho de
--     area suja so cria a linha PENDING em gis_dirty_area; o elo
--     area suja -> outbox e pendencia de integracao registrada na revisao
--     da P2.21 (correcao B1, caminho d: nao forcar a partir do SQL).
--
-- CONVENCOES DESTA MIGRATION (as mesmas das V13_5/V13_6):
--   * toda funcao de gatilho comeca fixando o search_path LOCAL da transacao
--     para o schema do tenant (TG_TABLE_SCHEMA), porque corpo plpgsql resolve
--     nomes no search_path da conexao em execucao e o caminho da app nao o
--     fixa — sem isto, as consultas sem qualificacao abaixo quebrariam fora
--     do migrador;
--   * distancia SEMPRE em metros por geography (ST_Distance sobre
--     geom::geography), como o §5.2 permite e a coluna gerada length_m da
--     V13_2 ja faz com o mesmo cast a partir do 4674;
--   * mensagens de erro sem diacriticos, como as da V13_2.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) Tabelas gis_dirty_area e gis_validation_issue (§3.4/§5.5)
-- ======================================================================
-- gis_dirty_area: uma linha por gravacao em feicao, com o envelope alterado
-- expandido com margem (ver a funcao gis_mark_dirty_area). O worker consome
-- as linhas PENDING pela outbox (desenho do ADR-0014; a publicacao da
-- mensagem e a pendencia de integracao registrada no cabecalho), roda as
-- validacoes do §5.4 so
-- nelas, grava os problemas em gis_validation_issue, recalcula zonas
-- derivadas e setores afetados, marca os tiles sujos (§10.1, GIS-06) e fecha
-- a area (DONE, ou FAILED com o motivo em process_note). processed_at e o
-- instante do fechamento (DONE ou FAILED).
--
-- feature_table/feature_id dizem QUAL feicao sujou a area (a geometria dela
-- ja vai no envelope): sem isso o worker nao saberia por onde comecar a
-- revalidacao incremental. Em DELETE, feature_id e o id da feicao removida
-- (a linha nao existe mais; o envelope e o que resta dela).

CREATE TABLE IF NOT EXISTS gis_dirty_area (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_table TEXT NOT NULL,
    feature_id UUID NOT NULL,
    envelope geometry(Geometry, 4674) NOT NULL,
    change_set_id UUID,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    process_note TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_dirty_area_tenant_id UNIQUE (tenant_id, id)
);

SELECT register_tenant_table('gis_dirty_area');

CREATE INDEX IF NOT EXISTS idx_gis_dirty_area_pending
    ON gis_dirty_area (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_gis_dirty_area_envelope
    ON gis_dirty_area USING GIST (envelope);
CREATE INDEX IF NOT EXISTS idx_gis_dirty_area_feature
    ON gis_dirty_area (tenant_id, feature_table, feature_id);

-- gis_validation_issue: problema encontrado pela validacao por area suja
-- (§5.4/§5.5). rule_code e o codigo estavel da regra (em ingles, como os
-- errors[].code da api: ex. NODE_WITHOUT_ARC, ARC_WITHOUT_NODE,
-- DN_MISMATCH_WITHOUT_FITTING); a frente de servico que grava e dona do
-- vocabulario — esta migration so fixa a forma da tabela.
-- feature2_*: a segunda feicao do problema quando ele e de relacao (dois
-- arcos sobrepostos, DN incompativel entre arco e peca); feature2_table
-- acompanha porque a segunda feicao pode ser de outra tabela.
-- feature_table/feature_id SEM FK, de proposito: o problema e polimorfo
-- sobre as 6 tabelas de feicao (mesmo desenho de gis_attachment e
-- gis_containment, que guardam tabela + id sem FK) e a feicao pode ser
-- removida (DELETE de PLANNED) sem apagar o problema ja registrado.
-- Aceite exige justificativa, autor e instante (§5.5: "cada problema pode
-- ser aceito com justificativa"); resolucao exige resolved_at. As duas
-- regras vao em CHECK, nao so na aplicacao, porque ha escrita fora da api.

CREATE TABLE IF NOT EXISTS gis_validation_issue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    rule_code TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('ERROR', 'WARNING')),
    feature_table TEXT NOT NULL,
    feature_id UUID NOT NULL,
    feature2_table TEXT,
    feature2_id UUID,
    envelope geometry(Geometry, 4674),
    change_set_id UUID,
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ACCEPTED', 'RESOLVED')),
    justification TEXT,
    accepted_by UUID,
    accepted_at TIMESTAMPTZ,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_validation_issue_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_validation_issue_accepted
        CHECK (status <> 'ACCEPTED'
               OR (justification IS NOT NULL AND accepted_by IS NOT NULL
                   AND accepted_at IS NOT NULL)),
    CONSTRAINT ck_gis_validation_issue_resolved
        CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL),
    CONSTRAINT ck_gis_validation_issue_feature2
        CHECK ((feature2_table IS NULL) = (feature2_id IS NULL))
);

SELECT register_tenant_table('gis_validation_issue');

CREATE INDEX IF NOT EXISTS idx_gis_validation_issue_open
    ON gis_validation_issue (tenant_id, status, severity, detected_at);
CREATE INDEX IF NOT EXISTS idx_gis_validation_issue_feature
    ON gis_validation_issue (tenant_id, feature_table, feature_id);
CREATE INDEX IF NOT EXISTS idx_gis_validation_issue_envelope
    ON gis_validation_issue USING GIST (envelope);

-- ======================================================================
-- Tolerancia de snap vigente (§5.2, passo 1; §3.10)
-- ======================================================================
-- Le a versao APPROVED vigente de gis.snap_tolerance_m em
-- parameter_versions (schema do tenant, V1_5): value e JSONB canonico e o
-- numero sai por (value #>> '{}')::numeric, como o mecanismo de parametros
-- grava. Sem versao aprovada vigente, vale o factory_default de
-- public.parameter_definitions (core V9_6: 0.10). A funcao NAO e de gatilho:
-- quem chama e funcao de gatilho, DEPOIS de fixar o search_path (ver o
-- cabecalho) — a resolucao de parameter_versions depende disso.
-- A guarda to_regclass segue o precedente do gate de pacote da V13_2
-- (gis_check_class_package): schema minimo sem a V1_5 (classpath parcial de
-- teste) cai no padrao de fabrica em vez de quebrar toda escrita GIS.
-- O COALESCE final com 0.10 so morde se o catalogo do core nao tiver a
-- chave (banco sem a V9_6): 0.10 e o factory_default registrado la, nao um
-- numero novo.

CREATE OR REPLACE FUNCTION gis_snap_tolerance_m(p_tenant_id UUID) RETURNS NUMERIC AS $$
DECLARE
    v_value NUMERIC;
BEGIN
    IF to_regclass('parameter_versions') IS NOT NULL THEN
        EXECUTE 'SELECT (value #>> ''{}'')::numeric FROM parameter_versions '
             || 'WHERE tenant_id = $1 AND definition_key = ''gis.snap_tolerance_m'' '
             || 'AND status = ''APPROVED'' AND valid_from <= CURRENT_DATE '
             || 'AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) '
             || 'ORDER BY valid_from DESC LIMIT 1'
            INTO v_value USING p_tenant_id;
    END IF;
    IF v_value IS NULL THEN
        SELECT (factory_default #>> '{}')::numeric INTO v_value
        FROM public.parameter_definitions
        WHERE definition_key = 'gis.snap_tolerance_m';
    END IF;
    RETURN COALESCE(v_value, 0.10);
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- (2) Area suja: toda gravacao em feicao marca o envelope alterado (§5.5)
-- ======================================================================
-- MARGEM: 10 m fixos em torno da geometria alterada (em UPDATE, da uniao da
-- geometria antiga com a nova; em DELETE, da antiga). Por que 10 m: e maior
-- que a tolerancia maxima de snap admitida (1,00 m, §3.10), entao cobre
-- qualquer vertice movido por snap; e alcanca os nos e as pontas de arco
-- imediatamente ligados a feicao alterada, que sao o que as validacoes de
-- conectividade do §5.4 precisam reexaminar. Validacao de componente e de
-- ilha NAO cabe em envelope nenhum — ela e por zona e o worker a dispara
-- pela area suja, nao dentro dela (§5.5). Se um dia a margem virar
-- parametro, a chave natural e uma gis.* nova no catalogo — nao inventada
-- aqui.
-- SRID: ST_Buffer sobre geography devolve geography, e o cast para geometry
-- pode sair em 4326 (o SRID canonico da geography); a geometria armazenada
-- e 4674. Por isso o ST_SetSRID(..., 4674) explicito no fim: SIRGAS 2000 e
-- WGS-84 diferem em centimetros, abaixo de qualquer margem desta tabela, e
-- o que a coluna exige e o rotulo 4674 correto — nunca transformar de
-- verdade (ST_Transform mudaria os numeros por grade, sem ganho aqui).

CREATE OR REPLACE FUNCTION gis_mark_dirty_area() RETURNS TRIGGER AS $$
DECLARE
    v_geom geometry;
    v_id UUID;
    v_change_set_id UUID;
    v_margin_m NUMERIC := 10;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);

    IF TG_TABLE_NAME = 'gis_structure' THEN
        -- gis_structure tem duas geometrias (poligono OU ponto): vale a que
        -- existir, como no gis_maintain_geom_web_structure da V13_2.
        IF TG_OP = 'INSERT' THEN
            v_geom := COALESCE(NEW.geom, NEW.geom_point);
        ELSIF TG_OP = 'DELETE' THEN
            v_geom := COALESCE(OLD.geom, OLD.geom_point);
        ELSE
            v_geom := ST_Collect(COALESCE(NEW.geom, NEW.geom_point),
                                 COALESCE(OLD.geom, OLD.geom_point));
        END IF;
    ELSE
        IF TG_OP = 'INSERT' THEN
            v_geom := NEW.geom;
        ELSIF TG_OP = 'DELETE' THEN
            v_geom := OLD.geom;
        ELSE
            v_geom := ST_Collect(NEW.geom, OLD.geom);
        END IF;
    END IF;
    IF v_geom IS NULL THEN
        RETURN NULL;
    END IF;
    v_id := COALESCE(NEW.id, OLD.id);
    v_change_set_id := COALESCE(NEW.change_set_id, OLD.change_set_id);

    INSERT INTO gis_dirty_area (tenant_id, feature_table, feature_id, envelope,
                                change_set_id, status)
    VALUES (COALESCE(NEW.tenant_id, OLD.tenant_id), TG_TABLE_NAME, v_id,
            ST_SetSRID(ST_Envelope(ST_Buffer(v_geom::geography, v_margin_m)::geometry), 4674),
            v_change_set_id, 'PENDING');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_node;
DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_arc;
DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_connection;
DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_structure;
DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_zone;
DROP TRIGGER IF EXISTS gis_dirty_trigger ON gis_poi;
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_structure
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_zone
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();
CREATE TRIGGER gis_dirty_trigger AFTER INSERT OR UPDATE OR DELETE ON gis_poi
    FOR EACH ROW EXECUTE FUNCTION gis_mark_dirty_area();

-- ======================================================================
-- (3) Snap topologico de arco e ramal (§5.2)
-- ======================================================================
-- Funcao auxiliar: cria (ou reaproveita, se ja existir exatamente no ponto)
-- o no JUNCTION de uma ponta de arco/ramal e devolve o id. Devolve NULL
-- quando o tenant nao tem tipo JUNCTION ATIVO — e quem chama decide o que
-- fazer (arco em operacao recusa; arco PLANNED e ramal seguem sem a ponta).
--
-- CRITERIO DE RECUSA (§5.2, passo 3: "sem no, cria JUNCTION (classes que
-- admitem) ou recusa a gravacao (classes que exigem peca cadastrada, por
-- regra)"): o modelo vigente NAO tem coluna que marque, por classe ou tipo
-- de arco, quem admite juncao automatica — gis_feature_class so tem
-- is_isolating/hydraulic_role e gis_feature_type so tem terminal_config
-- (dispositivos direcionais, que sao nos, nao arcos). Todas as classes de
-- arco de fabrica sao PIPE. O criterio adotado, portanto, e o estado do
-- catalogo do tenant: tipo JUNCTION ativo => juncao automatica admitida
-- (regra geral); tipo JUNCTION desativado pelo tenant => toda ponta tem de
-- cair num no cadastrado (peca, registro, PV...), e a obrigatoriedade do
-- §5.1 faz o resto. Ressalva registrada para a curadoria: se um dia houver
-- distincao por classe de arco, ela nasce como dado (coluna ou regra), nao
-- como nova migration de comportamento.
--
-- METADADOS DO JUNCTION CRIADO (sem inventar dado de captura): o no herda
-- do arco/ramal que o originou capture_method, source, exatidoes declaradas
-- (h/v), vertical_datum, captured_on, responsible_party_id, source_batch_id
-- e change_set_id — a juncao foi "capturada" exatamente como a feicao que a
-- gerou. accuracy_status NAO e copiado nem fixado: quem o calcula e o
-- gis_check_feature existente, no INSERT do no (DIGITIZED/CAD_IMPORT sem
-- exatidao viram APPROXIMATE pela regra do §3.8, como em qualquer feicao).
-- lifecycle_status acompanha o da feicao de origem (juncao de arco PLANNED
-- nasce PLANNED; a de arco IN_SERVICE nasce IN_SERVICE).
-- code: '<code da feicao>-J<ponta>' (ponta 1 ou 2; no ramal, 1 = rede e
-- 2 = ponta final), com sufixo -2, -3... em caso de colisao com code
-- existente no tenant (o code e unico por (tenant_id, code) em gis_node).
-- A busca inicial por no exatamente no ponto (ST_Equals) torna a funcao
-- idempotente: reprocessar a mesma ponta nao duplica a juncao.

CREATE OR REPLACE FUNCTION gis_ensure_junction(
    p_tenant_id UUID,
    p_point geometry,
    p_base_code TEXT,
    p_end_label TEXT,
    p_lifecycle_status TEXT,
    p_capture_method TEXT,
    p_source TEXT,
    p_h_accuracy_m NUMERIC,
    p_v_accuracy_m NUMERIC,
    p_vertical_datum TEXT,
    p_captured_on DATE,
    p_responsible_party_id UUID,
    p_source_batch_id UUID,
    p_change_set_id UUID,
    p_dma_id UUID
) RETURNS UUID AS $$
DECLARE
    v_type_id UUID;
    v_id UUID;
    v_code TEXT;
    v_n INT := 1;
BEGIN
    -- No abandonado nao e ponta valida de rede (correcao M2 da revisao):
    -- um no ABANDONED exatamente no ponto nao e reaproveitado como juncao.
    SELECT id INTO v_id FROM gis_node
    WHERE tenant_id = p_tenant_id AND ST_Equals(geom, p_point)
      AND lifecycle_status IS DISTINCT FROM 'ABANDONED'
    ORDER BY created_at LIMIT 1;
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;

    SELECT ft.id INTO v_type_id
    FROM gis_feature_type ft
    JOIN gis_feature_class fc ON fc.tenant_id = ft.tenant_id AND fc.id = ft.class_id
    WHERE ft.tenant_id = p_tenant_id AND fc.code = 'JUNCTION' AND ft.code = 'JUNCTION'
      AND ft.active AND fc.active
    LIMIT 1;
    IF v_type_id IS NULL THEN
        RETURN NULL;
    END IF;

    v_code := p_base_code || '-J' || p_end_label;
    WHILE EXISTS (SELECT 1 FROM gis_node WHERE tenant_id = p_tenant_id AND code = v_code) LOOP
        v_n := v_n + 1;
        v_code := p_base_code || '-J' || p_end_label || '-' || v_n;
    END LOOP;

    INSERT INTO gis_node (tenant_id, code, feature_type_id, lifecycle_status,
                          change_set_id, capture_method, h_accuracy_m, v_accuracy_m,
                          vertical_datum, captured_on, responsible_party_id,
                          source, source_batch_id, dma_id, geom)
    VALUES (p_tenant_id, v_code, v_type_id, p_lifecycle_status,
            p_change_set_id, p_capture_method, p_h_accuracy_m, p_v_accuracy_m,
            p_vertical_datum, p_captured_on, p_responsible_party_id,
            p_source, p_source_batch_id, p_dma_id, p_point)
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------
-- Snap do arco: para cada ponta, o no mais proximo ate a tolerancia; sem
-- no, JUNCTION; ao fim, a obrigatoriedade do §5.1. A busca de no ignora
-- nos ABANDONED (correcao M2): no abandonado nao e ponta valida de rede,
-- nem no arco nem no ramal (mesmo filtro em gis_snap_connection e no
-- reaproveitamento de gis_ensure_junction).
--
-- ORDEM DOS GATILHOS BEFORE na mesma tabela (o PostgreSQL dispara por nome
-- em ordem alfabetica): gis_arc_topology_trigger < gis_check_trigger <
-- gis_geom_web_trigger. Ou seja: o snap move o vertice ANTES de o
-- gis_maintain_geom_web recalcular o geom_web (que sai da geometria final)
-- e antes da validacao de atributos do gis_check_feature (que nao depende
-- de geometria nem de no). O INSERT de JUNCTION dentro do snap dispara a
-- cadeia normal do gis_node (check, geom_web, dirty) — desejado: a juncao
-- e uma feicao como outra qualquer.
-- IDEMPOTENCIA: ponta que ja coincide com o no esta a distancia 0, recebe
-- o mesmo id e o mesmo vertice — e o que torna seguro o INSERT/UPDATE de
-- arco feito pela divisao (item 4), cujas pontas ja nascem sobre nos.
-- ----------------------------------------------------------------------

CREATE OR REPLACE FUNCTION gis_snap_arc() RETURNS TRIGGER AS $$
DECLARE
    v_tol NUMERIC;
    v_point geometry;
    v_node_id UUID;
    v_dist NUMERIC;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    v_tol := gis_snap_tolerance_m(NEW.tenant_id);

    -- Ponta inicial (vertice 0).
    v_point := ST_StartPoint(NEW.geom);
    v_node_id := NULL; v_dist := NULL;
    SELECT n.id, ST_Distance(n.geom::geography, v_point::geography)
        INTO v_node_id, v_dist
    FROM gis_node n
    WHERE n.tenant_id = NEW.tenant_id
      AND n.lifecycle_status IS DISTINCT FROM 'ABANDONED'
      AND ST_Distance(n.geom::geography, v_point::geography) <= v_tol
    ORDER BY ST_Distance(n.geom::geography, v_point::geography)
    LIMIT 1;
    IF v_node_id IS NOT NULL THEN
        SELECT geom INTO v_point FROM gis_node WHERE tenant_id = NEW.tenant_id AND id = v_node_id;
        NEW.geom := ST_SetPoint(NEW.geom, 0, v_point);
        NEW.node_1_id := v_node_id;
    ELSE
        NEW.node_1_id := gis_ensure_junction(
            NEW.tenant_id, v_point, NEW.code, '1', NEW.lifecycle_status,
            NEW.capture_method, NEW.source, NEW.h_accuracy_m, NEW.v_accuracy_m,
            NEW.vertical_datum, NEW.captured_on, NEW.responsible_party_id,
            NEW.source_batch_id, NEW.change_set_id, NEW.dma_id);
        -- O vertice ja esta onde a juncao nasceu: nada a mover.
    END IF;

    -- Ponta final (ultimo vertice).
    v_point := ST_EndPoint(NEW.geom);
    v_node_id := NULL; v_dist := NULL;
    SELECT n.id, ST_Distance(n.geom::geography, v_point::geography)
        INTO v_node_id, v_dist
    FROM gis_node n
    WHERE n.tenant_id = NEW.tenant_id
      AND n.lifecycle_status IS DISTINCT FROM 'ABANDONED'
      AND ST_Distance(n.geom::geography, v_point::geography) <= v_tol
    ORDER BY ST_Distance(n.geom::geography, v_point::geography)
    LIMIT 1;
    IF v_node_id IS NOT NULL THEN
        SELECT geom INTO v_point FROM gis_node WHERE tenant_id = NEW.tenant_id AND id = v_node_id;
        NEW.geom := ST_SetPoint(NEW.geom, ST_NPoints(NEW.geom) - 1, v_point);
        NEW.node_2_id := v_node_id;
    ELSE
        NEW.node_2_id := gis_ensure_junction(
            NEW.tenant_id, v_point, NEW.code, '2', NEW.lifecycle_status,
            NEW.capture_method, NEW.source, NEW.h_accuracy_m, NEW.v_accuracy_m,
            NEW.vertical_datum, NEW.captured_on, NEW.responsible_party_id,
            NEW.source_batch_id, NEW.change_set_id, NEW.dma_id);
    END IF;

    -- Obrigatoriedade (§5.1 e item g da tarefa): arco em obra ou em
    -- operacao sem no numa das pontas nao entra. PLANNED pode ficar sem
    -- no (a ponta pode apontar para no ainda nao cadastrado); ABANDONED
    -- idem (legado retirado sem topologia completa nao pode travar a
    -- baixa). A mensagem diz o code e o estado para a api/operador
    -- acharem a feicao.
    IF NEW.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION')
       AND (NEW.node_1_id IS NULL OR NEW.node_2_id IS NULL) THEN
        RAISE EXCEPTION 'gis: arco % em % sem no nas duas pontas apos o snap (node_1_id/node_2_id obrigatorios)',
            NEW.code, NEW.lifecycle_status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_arc_topology_trigger ON gis_arc;
-- Correcao B2 da revisao: UPDATE OF lifecycle_status tambem dispara o
-- snap -- a obrigatoriedade de pontas do §5.1 tem de valer na TRANSICAO
-- PLANNED -> IN_SERVICE/UNDER_CONSTRUCTION, nao so na gravacao da
-- geometria. A funcao e idempotente: num update so de estado, o snap roda
-- sobre a geometria existente; ponta ja ligada esta a distancia 0 do
-- proprio no e ST_SetPoint com o mesmo ponto nao move nada; ponta sem no
-- tenta a juncao e, sem ela, a recusa do fim da funcao dispara.
CREATE TRIGGER gis_arc_topology_trigger BEFORE INSERT OR UPDATE OF geom, lifecycle_status ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_snap_arc();

-- ----------------------------------------------------------------------
-- Snap do ramal (gis_connection, §3.6.3): a geometria vai do arco de rede
-- (ponta inicial) ao ponto final (cavalete ou caixa de ligacao).
-- Escolhas documentadas:
--   * ponta inicial: no a ate a tolerancia => move o vertice e preenche
--     tap_node_id (o colar de tomada ou a juncao cadastrada na rede);
--     sem no, mas SOBRE um arco a ate a tolerancia => preenche arc_id com
--     esse arco (ramal ligado direto no tubo, sem peca cadastrada — o
--     §3.6.3 preve tap_node_id "quando cadastrado", ou seja, opcional);
--     com no, arc_id tambem e preenchido quando ha arco a ate a
--     tolerancia passando pelo ponto (e o arco onde o tap esta);
--   * ponta final: no a ate a tolerancia => move o vertice (o no final
--     costuma ser o METER_SETTING ou a caixa de ligacao; o ramal nao tem
--     coluna para esse no, o vinculo e geometrico ate a GIS-05 ligar o
--     ramal a ligacao); sem no => cria JUNCTION como no topologico neutro
--     (idem criterio do arco; criar METER_SETTING aqui inventaria um
--     cavalete que o cadastro nao declarou);
--   * SEM obrigatoriedade com RAISE: a regra do §5.1 e dos arcos; para o
--     ramal, ponta sem no e sem arco e problema de VALIDACAO (§5.4: ramal
--     sem ligacao, aviso), nao de gravacao — recusar aqui impediria
--     cadastrar o ramal projetado antes da rede.
-- Ordem alfabetica dos BEFORE: gis_check_trigger <
-- gis_connection_topology_trigger < gis_geom_web_trigger — o check nao
-- depende do snap e o geom_web sai da geometria final, como no arco.
-- ----------------------------------------------------------------------

CREATE OR REPLACE FUNCTION gis_snap_connection() RETURNS TRIGGER AS $$
DECLARE
    v_tol NUMERIC;
    v_point geometry;
    v_node_id UUID;
    v_arc_id UUID;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    v_tol := gis_snap_tolerance_m(NEW.tenant_id);

    -- Ponta inicial (lado da rede).
    v_point := ST_StartPoint(NEW.geom);
    SELECT n.id INTO v_node_id
    FROM gis_node n
    WHERE n.tenant_id = NEW.tenant_id
      AND n.lifecycle_status IS DISTINCT FROM 'ABANDONED'
      AND ST_Distance(n.geom::geography, v_point::geography) <= v_tol
    ORDER BY ST_Distance(n.geom::geography, v_point::geography)
    LIMIT 1;
    IF v_node_id IS NOT NULL THEN
        SELECT geom INTO v_point FROM gis_node WHERE tenant_id = NEW.tenant_id AND id = v_node_id;
        NEW.geom := ST_SetPoint(NEW.geom, 0, v_point);
        NEW.tap_node_id := v_node_id;
    END IF;
    IF NEW.arc_id IS NULL THEN
        SELECT a.id INTO v_arc_id
        FROM gis_arc a
        WHERE a.tenant_id = NEW.tenant_id
          AND ST_Distance(a.geom::geography, v_point::geography) <= v_tol
        ORDER BY ST_Distance(a.geom::geography, v_point::geography)
        LIMIT 1;
        IF v_arc_id IS NOT NULL THEN
            NEW.arc_id := v_arc_id;
        END IF;
    END IF;

    -- Ponta final (lado do imovel).
    v_point := ST_EndPoint(NEW.geom);
    SELECT n.id INTO v_node_id
    FROM gis_node n
    WHERE n.tenant_id = NEW.tenant_id
      AND n.lifecycle_status IS DISTINCT FROM 'ABANDONED'
      AND ST_Distance(n.geom::geography, v_point::geography) <= v_tol
    ORDER BY ST_Distance(n.geom::geography, v_point::geography)
    LIMIT 1;
    IF v_node_id IS NOT NULL THEN
        SELECT geom INTO v_point FROM gis_node WHERE tenant_id = NEW.tenant_id AND id = v_node_id;
        NEW.geom := ST_SetPoint(NEW.geom, ST_NPoints(NEW.geom) - 1, v_point);
    ELSE
        PERFORM gis_ensure_junction(
            NEW.tenant_id, v_point, NEW.code, '2', NEW.lifecycle_status,
            NEW.capture_method, NEW.source, NEW.h_accuracy_m, NEW.v_accuracy_m,
            NEW.vertical_datum, NEW.captured_on, NEW.responsible_party_id,
            NEW.source_batch_id, NEW.change_set_id, NEW.dma_id);
        -- Sem coluna de no final no ramal: a juncao existe no cadastro e a
        -- ponta ja coincide com ela; o retorno e descartado de proposito.
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_connection_topology_trigger ON gis_connection;
-- Idem B2 para o ramal: UPDATE OF lifecycle_status redispara o snap
-- (preenche tap_node_id/arc_id sobre a geometria existente), mas SEM
-- recusa -- como desenhado, ponta de ramal sem no e problema de
-- validacao (§5.4), nao de gravacao.
CREATE TRIGGER gis_connection_topology_trigger BEFORE INSERT OR UPDATE OF geom, lifecycle_status ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_snap_connection();

-- ======================================================================
-- (4) Divisao de arco por no novo sobre o arco (§5.2, passo 4)
-- ======================================================================
-- No inserido (ou movido) sobre um arco existente, a ate a tolerancia e sem
-- ser ponta ja ligada desse arco, divide o arco em dois (comportamento do
-- Giswater citado no §5.2):
--   * o arco ORIGINAL e encurtado por UPDATE ate o no divisor — o UPDATE
--     dispara o historico existente (gis_write_history), a area suja e o
--     snap (idempotente: as pontas ja estao sobre nos);
--   * um arco NOVO e inserido do no divisor ate a ponta final original,
--     com os MESMOS atributos: tipo, item de catalogo, nome, ciclo de vida,
--     estados, datas, pacote (change_set_id), attributes, metadados de
--     captura, funcao de rede, pavimento etc. (lista explicita de colunas,
--     licao da V13_7: nada de (NEW).* posicional);
--   * node_1_id/node_2_id: o original fica (no inicial, divisor) e o novo
--     fica (divisor, no final original);
--   * code do novo arco: '<code>-2'; havendo colisao, '-3', '-4'... (o
--     code e unico por (tenant_id, code); o laco garante um code livre sem
--     inventar esquema de numeracao novo);
--   * o que NAO se copia: legacy_object_id (o novo arco nao veio do
--     legado; copiar duplicaria a contagem da migracao da V13_2),
--     declared_length_m (era o comprimento declarado do arco INTEIRO; o
--     length_m calculado sai da geometria de cada pedaco) e
--     accuracy_status (o gis_check_feature recalcula no INSERT);
--   * cotas de fundo: o arco novo herda o downstream_invert_m original (a
--     ponta final e a mesma) e fica sem upstream_invert_m (a cota no
--     divisor e desconhecida — nao se interpola cota inventada); o
--     original perde o downstream_invert_m pela mesma razao e mantem o
--     upstream. A declividade (coluna gerada) recalcula sozinha quando
--     houver as duas cotas de novo.
-- FRACTION: ST_LineLocatePoint/ST_LineSubstring trabalham na geometria
-- 4674 (graus) — servem para LOCALIZAR o corte ao longo da linha; a
-- distancia que decide a divisao e a geografica, em metros. Depois do
-- corte, o vertice de juncao dos dois pedacos e forcado para a geometria
-- exata do no divisor (ST_SetPoint), entao a topologia gravada nao depende
-- da interpolacao em graus.
-- GUARDA DE RECURSAO: pg_trigger_depth() > 1 ignora nos criados DENTRO de
-- outro gatilho — em especial o JUNCTION que o snap do arco cria (ele
-- nasce numa ponta, nao sobre um arco, e processa-lo aqui so gastaria
-- varredura; pior: um JUNCTION criado sobre o cruzamento de outro arco
-- dividiria esse arco como efeito colateral de um INSERT de arco). A
-- divisao vale para a gravacao direta de no (INSERT, ou UPDATE de geom
-- quando o no e arrastado para cima de um arco), que e o passo 4 do §5.2.
-- Ramais (gis_connection) que apontavam para o arco dividido continuam
-- apontando para o pedaco original: religar ramal ao pedaco certo e da
-- GIS-05 (vinculo comercial), nao desta migration.

CREATE OR REPLACE FUNCTION gis_split_arcs_at_node() RETURNS TRIGGER AS $$
DECLARE
    v_tol NUMERIC;
    r RECORD;
    v_fraction DOUBLE PRECISION;
    v_first geometry;
    v_second geometry;
    v_code TEXT;
    v_n INT;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    IF pg_trigger_depth() > 1 THEN
        RETURN NULL;
    END IF;
    v_tol := gis_snap_tolerance_m(NEW.tenant_id);

    FOR r IN
        SELECT a.*
        FROM gis_arc a
        WHERE a.tenant_id = NEW.tenant_id
          -- Correcao M5 da revisao: arco ABANDONED nao e dividido. Ele e
          -- registro de rede retirada/legado; dividi-lo fabricaria dois
          -- arcos abandonados e historico de um ativo que nao existe.
          AND a.lifecycle_status IS DISTINCT FROM 'ABANDONED'
          AND (a.node_1_id IS NULL OR a.node_1_id <> NEW.id)
          AND (a.node_2_id IS NULL OR a.node_2_id <> NEW.id)
          AND ST_Distance(a.geom::geography, NEW.geom::geography) <= v_tol
          AND ST_Distance(ST_StartPoint(a.geom)::geography, NEW.geom::geography) > v_tol
          AND ST_Distance(ST_EndPoint(a.geom)::geography, NEW.geom::geography) > v_tol
    LOOP
        v_fraction := ST_LineLocatePoint(r.geom, NEW.geom);
        IF v_fraction <= 0 OR v_fraction >= 1 THEN
            CONTINUE;
        END IF;
        v_first := ST_LineSubstring(r.geom, 0, v_fraction);
        v_second := ST_LineSubstring(r.geom, v_fraction, 1);
        -- Linha que degenera em ponto nao e arco: com as pontas a mais de
        -- v_tol do divisor (filtro acima) isto nao deve ocorrer, mas o
        -- corte em graus pode degenerar linha quase nula — nao dividir.
        IF ST_GeometryType(v_first) <> 'ST_LineString'
           OR ST_GeometryType(v_second) <> 'ST_LineString'
           OR ST_NPoints(v_first) < 2 OR ST_NPoints(v_second) < 2 THEN
            CONTINUE;
        END IF;
        v_first := ST_SetPoint(v_first, ST_NPoints(v_first) - 1, NEW.geom);
        v_second := ST_SetPoint(v_second, 0, NEW.geom);

        v_code := r.code || '-2';
        v_n := 2;
        WHILE EXISTS (SELECT 1 FROM gis_arc WHERE tenant_id = NEW.tenant_id AND code = v_code) LOOP
            v_n := v_n + 1;
            v_code := r.code || '-' || v_n;
        END LOOP;

        -- O novo arco entra PRIMEIRO: se algo falhar nele, o original
        -- continua inteiro (a transacao desfaz tudo de qualquer forma, mas
        -- a ordem deixa o historico legivel: criacao, depois encurtamento).
        INSERT INTO gis_arc (tenant_id, code, feature_type_id, catalog_item_id,
            name, lifecycle_status, retired_reason, operational_status,
            normal_status, condition_rating, installed_on,
            installed_on_precision, abandoned_on, change_set_id, attributes,
            schema_version, capture_method, h_accuracy_m, v_accuracy_m,
            vertical_datum, captured_on, responsible_party_id, source,
            source_batch_id, source_ref, asset_codes, dma_id,
            node_1_id, node_2_id, declared_length_m, upstream_invert_m,
            downstream_invert_m, pressurized, flow_direction, network_function,
            depth_m, street_side, dist_to_property_line_m, dist_to_curb_m,
            pavement_type, geom, created_by, updated_by)
        VALUES (r.tenant_id, v_code, r.feature_type_id, r.catalog_item_id,
            r.name, r.lifecycle_status, r.retired_reason, r.operational_status,
            r.normal_status, r.condition_rating, r.installed_on,
            r.installed_on_precision, r.abandoned_on, r.change_set_id, r.attributes,
            r.schema_version, r.capture_method, r.h_accuracy_m, r.v_accuracy_m,
            r.vertical_datum, r.captured_on, r.responsible_party_id, r.source,
            r.source_batch_id, r.source_ref, r.asset_codes, r.dma_id,
            NEW.id, r.node_2_id, NULL, NULL,
            r.downstream_invert_m, r.pressurized, r.flow_direction, r.network_function,
            r.depth_m, r.street_side, r.dist_to_property_line_m, r.dist_to_curb_m,
            r.pavement_type, v_second, r.created_by, r.updated_by);

        UPDATE gis_arc
        SET geom = v_first, node_2_id = NEW.id, downstream_invert_m = NULL
        WHERE tenant_id = NEW.tenant_id AND id = r.id;
    END LOOP;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_node_split_trigger ON gis_node;
CREATE TRIGGER gis_node_split_trigger AFTER INSERT ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_split_arcs_at_node();
DROP TRIGGER IF EXISTS gis_node_split_geom_trigger ON gis_node;
CREATE TRIGGER gis_node_split_geom_trigger AFTER UPDATE OF geom ON gis_node
    FOR EACH ROW WHEN (OLD.geom IS DISTINCT FROM NEW.geom)
    EXECUTE FUNCTION gis_split_arcs_at_node();

-- ======================================================================
-- (5) Arrastar pontas: mover o no move os vertices ligados (§5.2, passo 5)
-- ======================================================================
-- AFTER UPDATE OF geom em gis_node: todo arco com node_1_id = no movido
-- tem o vertice 0 substituido pela nova posicao; idem o ultimo vertice
-- para node_2_id. O UPDATE no arco dispara a cadeia normal dele (snap
-- idempotente — a ponta esta exatamente sobre o no —, historico, area
-- suja, geom_web). Ramal ligado pelo tap_node_id tem a ponta inicial
-- arrastada junto; a ponta FINAL do ramal nao tem coluna de no (ver o
-- snap do ramal), entao nao ha como identifica-la sem ambiguidade — fica
-- registrada a limitacao, a resolver na GIS-05.
-- Sem guarda de profundidade: nada alem da gravacao direta muda geom de
-- no nesta migration (o snap move vertice de ARCO, nunca de no), entao
-- nao ha ciclo possivel por aqui.

CREATE OR REPLACE FUNCTION gis_drag_arc_endpoints() RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);

    UPDATE gis_arc
    SET geom = ST_SetPoint(geom, 0, NEW.geom)
    WHERE tenant_id = NEW.tenant_id AND node_1_id = NEW.id
      AND NOT ST_Equals(ST_StartPoint(geom), NEW.geom);

    UPDATE gis_arc
    SET geom = ST_SetPoint(geom, ST_NPoints(geom) - 1, NEW.geom)
    WHERE tenant_id = NEW.tenant_id AND node_2_id = NEW.id
      AND NOT ST_Equals(ST_EndPoint(geom), NEW.geom);

    UPDATE gis_connection
    SET geom = ST_SetPoint(geom, 0, NEW.geom)
    WHERE tenant_id = NEW.tenant_id AND tap_node_id = NEW.id
      AND NOT ST_Equals(ST_StartPoint(geom), NEW.geom);

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_node_drag_trigger ON gis_node;
CREATE TRIGGER gis_node_drag_trigger AFTER UPDATE OF geom ON gis_node
    FOR EACH ROW WHEN (OLD.geom IS DISTINCT FROM NEW.geom)
    EXECUTE FUNCTION gis_drag_arc_endpoints();

-- ======================================================================
-- (6) View gis_network_element: mesma definicao da V13_2 + node_1_code e
-- node_2_code NO FIM (colunas novas por ultimo: CREATE OR REPLACE VIEW so
-- aceita acrescentar no fim, e a entidade JPA le por nome, nao por posicao)
-- ======================================================================
-- node_1_code/node_2_code: no ramo gis_arc, o code dos nos das pontas (JOIN
-- com gis_node por (tenant_id, id)); nos demais ramos, NULL (no, estrutura
-- e ramal nao tem pontas de arco). A definicao abaixo e a da V13_2 copiada
-- sem nenhuma outra alteracao alem dessas duas colunas e dos dois JOINs do
-- ramo do arco.
CREATE OR REPLACE VIEW gis_network_element
WITH (security_invoker = true) AS
SELECT n.id, n.tenant_id, n.tenant_schema, n.created_at, n.updated_at, n.version,
    n.created_by, n.updated_by, n.code AS element_code, n.name,
    CASE WHEN cs.id IS NOT NULL THEN CASE cft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GATE_VALVE' THEN 'VALVE'
        WHEN 'PRV' THEN 'PRESSURE_REDUCING_VALVE'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF' THEN 'BLOWOFF_VALVE'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT_PILLAR' THEN 'HYDRANT'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        WHEN 'OVERFLOW' THEN 'OVERFLOW_WEIR'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE_ADAPTER' THEN 'FLANGE'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        WHEN 'PRESSURE_POINT' THEN 'MONITORING_POINT'
        WHEN 'QUALITY_POINT' THEN 'MONITORING_POINT'
        ELSE 'NODE' END
    ELSE CASE ft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GATE_VALVE' THEN 'VALVE'
        WHEN 'PRV' THEN 'PRESSURE_REDUCING_VALVE'
        WHEN 'AIR_VALVE' THEN 'AIR_VALVE'
        WHEN 'BLOWOFF' THEN 'BLOWOFF_VALVE'
        WHEN 'CHECK_VALVE' THEN 'CHECK_VALVE'
        WHEN 'HYDRANT_PILLAR' THEN 'HYDRANT'
        WHEN 'MACRO_METER' THEN 'MACRO_METER'
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'MANHOLE' THEN 'MANHOLE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        WHEN 'GREASE_TRAP' THEN 'GREASE_TRAP'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        WHEN 'OVERFLOW' THEN 'OVERFLOW_WEIR'
        WHEN 'JUNCTION' THEN 'JUNCTION'
        WHEN 'REDUCER' THEN 'REDUCER'
        WHEN 'TEE' THEN 'TEE'
        WHEN 'CROSS' THEN 'CROSS'
        WHEN 'CAP' THEN 'CAP'
        WHEN 'FLANGE_ADAPTER' THEN 'FLANGE'
        WHEN 'COUPLING' THEN 'COUPLING'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        WHEN 'PRESSURE_POINT' THEN 'MONITORING_POINT'
        WHEN 'QUALITY_POINT' THEN 'MONITORING_POINT'
        ELSE 'NODE' END
    END AS element_type,
    n.geom AS geometry, n.dma_id,
    NULLIF(COALESCE(m.code, cm.code), 'UNKNOWN') AS material,
    COALESCE(ci.dn_mm, cci.dn_mm) AS diameter_mm,
    NULL::numeric AS length_m,
    n.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN n.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, n.installed_on))::int END AS age_years,
    n.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE n.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    n.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(n.attributes || jsonb_build_object(
        'elevation_m', n.ground_elevation_m,
        'setting', n.setting,
        'power_kw', cs.power_kw,
        'diameter_m', CASE WHEN COALESCE(ci.dn_mm, cci.dn_mm) IS NOT NULL
            THEN COALESCE(ci.dn_mm, cci.dn_mm) / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due,
    NULL::text AS node_1_code, NULL::text AS node_2_code
FROM gis_node n
JOIN gis_feature_type ft ON ft.tenant_id = n.tenant_id AND ft.id = n.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = n.tenant_id AND ci.id = n.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = n.tenant_id AND m.id = ci.material_id
LEFT JOIN gis_containment c ON c.tenant_id = n.tenant_id
    AND c.contained_table = 'gis_node' AND c.contained_id = n.id AND c.relation = 'CONTAINS'
LEFT JOIN gis_structure cs ON cs.tenant_id = n.tenant_id AND cs.id = c.container_id
LEFT JOIN gis_feature_type cft ON cft.tenant_id = cs.tenant_id AND cft.id = cs.feature_type_id
LEFT JOIN gis_catalog_item cci ON cci.tenant_id = cs.tenant_id AND cci.id = cs.catalog_item_id
LEFT JOIN gis_material cm ON cm.tenant_id = cs.tenant_id AND cm.id = cci.material_id
UNION ALL
SELECT a.id, a.tenant_id, a.tenant_schema, a.created_at, a.updated_at, a.version,
    a.created_by, a.updated_by, a.code AS element_code, a.name,
    CASE ft.code
        WHEN 'WATER_MAIN' THEN 'PIPE'
        WHEN 'GRAVITY_SEWER' THEN 'SEWER_PIPE'
        WHEN 'FORCE_MAIN' THEN 'SEWER_PIPE'
        WHEN 'INVERTED_SIPHON' THEN 'SIPHON'
        ELSE 'PIPE' END AS element_type,
    a.geom AS geometry, a.dma_id, NULLIF(m.code, 'UNKNOWN') AS material, ci.dn_mm AS diameter_mm,
    COALESCE(a.declared_length_m, a.length_m) AS length_m,
    a.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN a.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, a.installed_on))::int END AS age_years,
    a.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE a.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    a.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(a.attributes || jsonb_build_object(
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due,
    n1.code AS node_1_code, n2.code AS node_2_code
FROM gis_arc a
JOIN gis_feature_type ft ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = a.tenant_id AND ci.id = a.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = a.tenant_id AND m.id = ci.material_id
LEFT JOIN gis_node n1 ON n1.tenant_id = a.tenant_id AND n1.id = a.node_1_id
LEFT JOIN gis_node n2 ON n2.tenant_id = a.tenant_id AND n2.id = a.node_2_id
UNION ALL
SELECT s.id, s.tenant_id, s.tenant_schema, s.created_at, s.updated_at, s.version,
    s.created_by, s.updated_by, s.code AS element_code, s.name,
    CASE ft.code
        WHEN 'RESERVOIR' THEN 'RESERVOIR'
        WHEN 'ELEVATED_TANK' THEN 'TANK'
        WHEN 'WATER_PUMP_STATION' THEN 'PUMP_STATION'
        WHEN 'WTP' THEN 'TREATMENT_PLANT'
        WHEN 'WELL' THEN 'WELL'
        WHEN 'SPRING' THEN 'SPRING'
        WHEN 'SURFACE_INTAKE' THEN 'INTAKE'
        WHEN 'SEWAGE_LIFT_STATION' THEN 'LIFT_STATION'
        WHEN 'WWTP' THEN 'SEWER_TREATMENT_PLANT'
        WHEN 'PIPE_SUPPORT' THEN 'SUPPORT'
        WHEN 'ANCHOR_BLOCK' THEN 'ANCHOR'
        WHEN 'THRUST_BLOCK' THEN 'THRUST_BLOCK'
        WHEN 'CATHODIC_PROTECTION' THEN 'CATHODIC_PROTECTION'
        ELSE 'NODE' END AS element_type,
    COALESCE(s.geom, s.geom_point) AS geometry, s.dma_id, NULLIF(m.code, 'UNKNOWN') AS material,
    ci.dn_mm AS diameter_mm, NULL::numeric AS length_m,
    s.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN s.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, s.installed_on))::int END AS age_years,
    NULL::numeric AS depth_m, NULL::numeric AS pressure_rating_bar,
    CASE s.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    s.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(s.attributes || jsonb_build_object(
        'head_m', s.head_m,
        'power_kw', s.power_kw,
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due,
    NULL::text AS node_1_code, NULL::text AS node_2_code
FROM gis_structure s
JOIN gis_feature_type ft ON ft.tenant_id = s.tenant_id AND ft.id = s.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = s.tenant_id AND ci.id = s.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = s.tenant_id AND m.id = ci.material_id
WHERE NOT EXISTS (
    SELECT 1 FROM gis_containment c
    WHERE c.tenant_id = s.tenant_id AND c.container_table = 'gis_structure'
      AND c.container_id = s.id AND c.relation = 'CONTAINS'
)
UNION ALL
SELECT cn.id, cn.tenant_id, cn.tenant_schema, cn.created_at, cn.updated_at, cn.version,
    cn.created_by, cn.updated_by, cn.code AS element_code, cn.name,
    CASE ft.code
        WHEN 'WATER_SERVICE_LINE' THEN 'SERVICE_LINE'
        WHEN 'SEWER_SERVICE_LINE' THEN 'SEWER_CONNECTION'
        ELSE 'SERVICE_LINE' END AS element_type,
    cn.geom AS geometry, cn.dma_id, NULLIF(m.code, 'UNKNOWN') AS material, ci.dn_mm AS diameter_mm,
    cn.length_m,
    cn.installed_on::timestamp AT TIME ZONE 'UTC' AS installation_date,
    CASE WHEN cn.installed_on IS NULL THEN NULL
         ELSE DATE_PART('year', age(CURRENT_DATE, cn.installed_on))::int END AS age_years,
    cn.depth_m, NULL::numeric AS pressure_rating_bar,
    CASE cn.lifecycle_status
        WHEN 'IN_SERVICE' THEN 'ACTIVE'
        WHEN 'UNDER_CONSTRUCTION' THEN 'UNDER_CONSTRUCTION'
        WHEN 'PLANNED' THEN 'PLANNED'
        ELSE 'ABANDONED' END AS status,
    cn.condition_rating, NULL::text AS criticality,
    jsonb_strip_nulls(cn.attributes || jsonb_build_object(
        'diameter_m', CASE WHEN ci.dn_mm IS NOT NULL THEN ci.dn_mm / 1000.0 END)) AS properties,
    NULL::timestamptz AS last_inspection_at,
    NULL::timestamptz AS next_inspection_due,
    NULL::text AS node_1_code, NULL::text AS node_2_code
FROM gis_connection cn
JOIN gis_feature_type ft ON ft.tenant_id = cn.tenant_id AND ft.id = cn.feature_type_id
LEFT JOIN gis_catalog_item ci ON ci.tenant_id = cn.tenant_id AND ci.id = cn.catalog_item_id
LEFT JOIN gis_material m ON m.tenant_id = cn.tenant_id AND m.id = ci.material_id;

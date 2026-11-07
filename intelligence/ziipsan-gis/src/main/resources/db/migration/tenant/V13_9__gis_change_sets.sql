-- GIS-04 (P2.22) — pacotes de edicao, revisao por outra pessoa, ciclo de
-- vida, travas por area e "rede na data" (GIS-CADASTRO-TECNICO §3.7, §6.1
-- a §6.3 e §13.2 linha GIS-04).
--
-- NUMERACAO: a spec reservava V13_4 para esta migration, mas no repo real
-- V13_3 e V13_4 ja foram consumidos (gis_responsible_party_redaction e
-- gis_catalog_approval), V13_5 a V13_7 sao os fixes da integracao P1c e
-- V13_8 e a GIS-03 (P2.21). Seguindo o precedente do projeto (slot ocupado
-- -> proxima versao livre da serie, sem editar migration aplicada), esta
-- migration e a V13_9. Evidencia: listagem MCP de
-- intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/ na main
-- (HEAD 0fbcd22) em 07/11/2026 — ver RELATORIO-P2.22.md.
--
-- DESENHO:
-- (1) gis_change_set / gis_change_set_item / gis_change_set_review /
--     gis_edit_lock (§3.4): o pacote e a unidade de mudanca da rede em
--     operacao (decisao 3 do ADR-0034). Feicoes NOVAS existem nas tabelas
--     como PLANNED com o change_set_id (§6.1); alteracoes e retiradas de
--     feicoes em operacao vivem como estado proposto no item ate a
--     aplicacao.
-- (2) Guarda de pacote (gis_package_guard): gravar feicao IN_SERVICE ou
--     UNDER_CONSTRUCTION fora da aplicacao de um pacote e recusado pelo
--     banco, inclusive por SQL direto. A aplicacao sinaliza-se pela GUC
--     transacional app.gis_applying_change_set = '<uuid do pacote>'.
--     CORRECAO A2 (revisao): a GUC NAO e segredo — qualquer sessao pode
--     liga-la com set_config. Por isso a guarda nao confia so nela:
--     exige que o pacote exista no tenant da feicao, esteja em estado
--     aplicavel (IN_REVIEW/APPROVED, os mesmos que gis_apply_change_set
--     aceita) e — na gravacao raiz (profundidade de gatilho 1) — que a
--     feicao seja item daquele pacote e que o change_set_id gravado
--     seja o do pacote aplicante. Gravacoes em profundidade > 1 sao
--     efeitos colaterais de gatilho dentro da aplicacao (a juncao que o
--     snap da V13_8 cria, as pontas que o arrasto move): herdam o pacote
--     aplicante ja conferido na raiz. LIMITACAO RESIDUAL registrada:
--     quem tem escrita SQL direta E monta um pacote legitimo em
--     IN_REVIEW com a feicao como item pode ligar a GUC e gravar sem
--     passar pelos portoes de gis_apply_change_set (validacao e
--     revisor <> autor sao conferidos la e nas guardas de revisao) —
--     a GUC continua sendo um sinal cooperativo, nao uma credencial;
--     fecha-la de vez exigiria funcao SECURITY DEFINER dona da escrita,
--     desenho fora desta migration.
-- (3) Revisao por outra pessoa: gatilho em gis_change_set_review e na
--     transicao do pacote recusam revisor = autor; a api devolve 403.
-- (4) gis_apply_change_set: aplica TODOS os itens na transacao de quem
--     chama (a api chama dentro da sua transacao; qualquer erro desfaz
--     tudo). Item cuja feicao mudou depois da versao de base
--     (version <> base_version) exige rebase e aborta a aplicacao.
--     Problema de validacao ERROR aberto do pacote bloqueia; WARNING
--     aberto exige justificativa do revisor.
-- (5) Trava por area: gis_edit_lock ativa (nao expirada) de pacote aberto
--     impede outro pacote de gravar feicao em operacao na area, e locks
--     ativos sobrepostos de pacotes distintos nao coexistem.
-- (6) Rede na data: gis_network_at_recorded (tempo do sistema, sobre o
--     gis_feature_at da V13_2) e gis_network_at_business (tempo do
--     negocio: installed_on/abandoned_on + ciclo IN_SERVICE do snapshot).
--     Feicao PLANNED/UNDER_CONSTRUCTION nunca aparece: esta fora do
--     tracado e dos indicadores (§3.7).
-- (7) View gis_change_set_pending_review: superficie de leitura dos
--     pacotes aguardando revisao para as "minhas tarefas" da P2.28
--     (PLT-13) — a tela/fila da P2.28 e de outra tarefa; aqui fica o
--     estado e a consulta.
--
-- CONVENCOES DESTA MIGRATION (as mesmas das V13_5/V13_6/V13_8; correcao
-- A1 da revisao): toda funcao de gatilho comeca fixando o search_path
-- LOCAL da transacao para o schema do tenant (TG_TABLE_SCHEMA), porque
-- corpo plpgsql resolve nomes no search_path da conexao em execucao e o
-- caminho da app nao o fixa. As funcoes chamadas pela api que nao sao
-- de gatilho (gis_apply_change_set e as de rede na data) fixam o
-- search_path a partir do schema do tenant resolvido em
-- public.tenant_catalog pela GUC app.current_tenant (com fallback para
-- o schema corrente, o caminho do migrador), e gis_apply_change_set
-- passa esse schema EXPLICITAMENTE a gis_apply_change_set_item
-- (correcao M1: nada de current_schema() solto no meio da aplicacao).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) Tabelas do pacote (§3.4)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_change_set (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    type TEXT NOT NULL CHECK (type IN
        ('PROJECT', 'AS_BUILT', 'CORRECTION', 'IMPORT', 'FIELD', 'QGIS',
         'SERVICE_ORDER', 'AUTO_SERVICE_LINE')),
    channel TEXT NOT NULL CHECK (channel IN
        ('WEB', 'QGIS', 'FIELD', 'IMPORT', 'API', 'SERVICE_ORDER', 'AUTO')),
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'APPLIED',
         'RETURNED', 'REJECTED', 'CANCELLED')),
    author_id UUID NOT NULL,
    extent geometry(Geometry, 4674),
    service_order_ref TEXT,
    project_ref TEXT,
    technical_report TEXT,
    quality_report JSONB,
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_change_set_tenant_id UNIQUE (tenant_id, id)
);

SELECT register_tenant_table('gis_change_set');

CREATE INDEX IF NOT EXISTS idx_gis_change_set_status
    ON gis_change_set (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_gis_change_set_author
    ON gis_change_set (tenant_id, author_id, status);
CREATE INDEX IF NOT EXISTS idx_gis_change_set_extent
    ON gis_change_set USING GIST (extent);

-- proposed_state: colunas escalares propostas da feicao (JSONB via classe
-- Json do core na aplicacao; o banco so exige objeto). A geometria vai em
-- WKT (SRID 4674) em proposed_geom_wkt — e, para gis_structure com ponto,
-- proposed_geom_point_wkt — porque JSONB nao guarda geometria com tipo e
-- SRID sem perda. base_snapshot e o to_jsonb da feicao na criacao do item
-- (o "antes" da tela de revisao); base_version e a coluna version da
-- feicao nesse instante (controle otimista do §6.3).
CREATE TABLE IF NOT EXISTS gis_change_set_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    change_set_id UUID NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'RETIRE')),
    feature_table TEXT NOT NULL CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure',
         'gis_zone', 'gis_poi')),
    feature_id UUID NOT NULL,
    base_version BIGINT,
    base_snapshot JSONB,
    proposed_state JSONB NOT NULL DEFAULT '{}',
    proposed_geom_wkt TEXT,
    proposed_geom_point_wkt TEXT,
    validation_result JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_change_set_item_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_change_set_item_set
        FOREIGN KEY (tenant_id, change_set_id)
        REFERENCES gis_change_set (tenant_id, id),
    CONSTRAINT ck_gis_change_set_item_base
        CHECK ((operation = 'INSERT' AND base_version IS NULL)
               OR (operation <> 'INSERT' AND base_version IS NOT NULL)),
    CONSTRAINT ck_gis_change_set_item_proposed_object
        CHECK (jsonb_typeof(proposed_state) = 'object')
);

SELECT register_tenant_table('gis_change_set_item');

CREATE INDEX IF NOT EXISTS idx_gis_change_set_item_set
    ON gis_change_set_item (tenant_id, change_set_id);
CREATE INDEX IF NOT EXISTS idx_gis_change_set_item_feature
    ON gis_change_set_item (tenant_id, feature_table, feature_id);

CREATE TABLE IF NOT EXISTS gis_change_set_review (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    change_set_id UUID NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('APPROVED', 'RETURNED', 'REJECTED')),
    reviewer_id UUID NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_change_set_review_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_change_set_review_set
        FOREIGN KEY (tenant_id, change_set_id)
        REFERENCES gis_change_set (tenant_id, id),
    CONSTRAINT ck_gis_change_set_review_reason
        CHECK (decision = 'APPROVED' OR (reason IS NOT NULL AND btrim(reason) <> ''))
);

SELECT register_tenant_table('gis_change_set_review');

CREATE INDEX IF NOT EXISTS idx_gis_change_set_review_set
    ON gis_change_set_review (tenant_id, change_set_id, created_at);

CREATE TABLE IF NOT EXISTS gis_edit_lock (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    change_set_id UUID NOT NULL,
    geom geometry(Geometry, 4674) NOT NULL,
    owner_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_edit_lock_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_edit_lock_set
        FOREIGN KEY (tenant_id, change_set_id)
        REFERENCES gis_change_set (tenant_id, id),
    CONSTRAINT ck_gis_edit_lock_expiry CHECK (expires_at > created_at)
);

SELECT register_tenant_table('gis_edit_lock');

CREATE INDEX IF NOT EXISTS idx_gis_edit_lock_geom
    ON gis_edit_lock USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_edit_lock_active
    ON gis_edit_lock (tenant_id, change_set_id, expires_at);

-- change_set_id das feicoes ganha a FK que a V13_2 adiou para ca.
ALTER TABLE gis_node
    ADD CONSTRAINT fk_gis_node_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);
ALTER TABLE gis_arc
    ADD CONSTRAINT fk_gis_arc_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);
ALTER TABLE gis_connection
    ADD CONSTRAINT fk_gis_connection_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);
ALTER TABLE gis_structure
    ADD CONSTRAINT fk_gis_structure_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);
ALTER TABLE gis_zone
    ADD CONSTRAINT fk_gis_zone_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);
ALTER TABLE gis_poi
    ADD CONSTRAINT fk_gis_poi_change_set
    FOREIGN KEY (tenant_id, change_set_id) REFERENCES gis_change_set (tenant_id, id);

-- ======================================================================
-- (2) Guarda de pacote e ciclo de vida nas feicoes
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_package_guard() RETURNS TRIGGER AS $$
DECLARE
    v_applying UUID;
    v_applying_ok BOOLEAN := FALSE;
    v_geom geometry;
    v_lock UUID;
BEGIN
    -- Convencao da serie (V13_6/V13_8): fixa o search_path da transacao
    -- no schema do tenant antes de qualquer consulta sem qualificacao.
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);

    -- GUC transacional que gis_apply_change_set liga com o id do pacote.
    -- Correcao A2: a GUC sozinha NAO basta (qualquer sessao a liga);
    -- v_applying_ok exige pacote existente no tenant, em estado
    -- aplicavel, e a feicao como item dele na gravacao raiz — ver o
    -- cabecalho (2) para a regra e a limitacao residual.
    BEGIN
        v_applying := NULLIF(current_setting('app.gis_applying_change_set', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_applying := NULL;
    END;
    IF v_applying IS NOT NULL AND TG_OP <> 'DELETE' THEN
        v_applying_ok := EXISTS (
            SELECT 1 FROM gis_change_set cs
            WHERE cs.id = v_applying AND cs.tenant_id = NEW.tenant_id
              AND cs.status IN ('IN_REVIEW', 'APPROVED'))
        AND (
            -- Efeito colateral de gatilho dentro da aplicacao (snap que
            -- cria juncao, arrasto que move ponta): a gravacao raiz ja
            -- foi conferida; basta o pacote aplicante valido.
            pg_trigger_depth() > 1
            OR (v_applying IS NOT DISTINCT FROM NEW.change_set_id
                AND EXISTS (
                    SELECT 1 FROM gis_change_set_item i
                    WHERE i.change_set_id = v_applying
                      AND i.tenant_id = NEW.tenant_id
                      AND i.feature_table = TG_TABLE_NAME
                      AND i.feature_id = NEW.id)));
    END IF;

    IF TG_OP = 'DELETE' THEN
        -- Feicao nunca e apagada fisicamente; DELETE so em PLANNED de
        -- pacote nao aprovado (§3.7).
        IF OLD.lifecycle_status <> 'PLANNED' THEN
            RAISE EXCEPTION 'gis: feicao % em % nao pode ser apagada (ciclo %)',
                OLD.code, TG_TABLE_NAME, OLD.lifecycle_status;
        END IF;
        IF OLD.change_set_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM gis_change_set cs
            WHERE cs.id = OLD.change_set_id AND cs.tenant_id = OLD.tenant_id
              AND cs.status IN ('APPROVED', 'APPLIED')) THEN
            RAISE EXCEPTION 'gis: feicao % pertence a pacote aprovado; exclusao recusada',
                OLD.code;
        END IF;
        RETURN OLD;
    END IF;

    -- Transicoes de ciclo de vida do §3.7 (UPDATE que muda o ciclo).
    IF TG_OP = 'UPDATE' AND NEW.lifecycle_status IS DISTINCT FROM OLD.lifecycle_status THEN
        IF NOT (
            (OLD.lifecycle_status = 'PLANNED' AND NEW.lifecycle_status IN ('UNDER_CONSTRUCTION', 'IN_SERVICE', 'ABANDONED'))
            OR (OLD.lifecycle_status = 'UNDER_CONSTRUCTION' AND NEW.lifecycle_status = 'IN_SERVICE')
            OR (OLD.lifecycle_status = 'IN_SERVICE' AND NEW.lifecycle_status = 'ABANDONED')
        ) THEN
            RAISE EXCEPTION 'gis: transicao de ciclo de vida invalida: % -> % (feicao %)',
                OLD.lifecycle_status, NEW.lifecycle_status, OLD.code;
        END IF;
    END IF;

    -- Gravação em operação fora de pacote aplicado: recusada.
    IF NOT v_applying_ok THEN
        IF TG_OP = 'INSERT' AND NEW.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION') THEN
            RAISE EXCEPTION 'gis: feicao % nasce em % fora de pacote aplicado; rede em operacao so muda por pacote revisado (GIS-04)',
                NEW.code, NEW.lifecycle_status;
        END IF;
        IF TG_OP = 'UPDATE' AND (
            OLD.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION')
            OR NEW.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION')) THEN
            RAISE EXCEPTION 'gis: feicao % em operacao so muda por pacote aplicado; gravacao direta recusada (GIS-04)',
                OLD.code;
        END IF;
    END IF;

    -- Trava por area: feicao em operacao (ou que entra em operacao) nao
    -- pode ser gravada dentro de area travada por OUTRO pacote aberto.
    IF NEW.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION')
       OR (TG_OP = 'UPDATE' AND OLD.lifecycle_status IN ('IN_SERVICE', 'UNDER_CONSTRUCTION')) THEN
        -- Todas as tabelas de feicao tem geom; em gis_structure, sem
        -- poligono, vale o geom_point.
        v_geom := NEW.geom;
        IF TG_TABLE_NAME = 'gis_structure' AND v_geom IS NULL THEN
            v_geom := NEW.geom_point;
        END IF;
        IF v_geom IS NOT NULL THEN
            SELECT l.id INTO v_lock
            FROM gis_edit_lock l
            JOIN gis_change_set cs
              ON cs.id = l.change_set_id AND cs.tenant_id = l.tenant_id
            WHERE l.tenant_id = NEW.tenant_id
              AND l.expires_at > clock_timestamp()
              AND cs.status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'RETURNED', 'APPROVED')
              AND l.change_set_id IS DISTINCT FROM NEW.change_set_id
              AND ST_Intersects(l.geom, v_geom)
            LIMIT 1;
            IF v_lock IS NOT NULL THEN
                RAISE EXCEPTION 'gis: area travada por outro pacote (trava %); gravacao da feicao % recusada',
                    v_lock, NEW.code;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_node;
DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_arc;
DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_connection;
DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_structure;
DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_zone;
DROP TRIGGER IF EXISTS gis_package_guard_trigger ON gis_poi;
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_node
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_arc
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_connection
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_structure
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_zone
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();
CREATE TRIGGER gis_package_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_poi
    FOR EACH ROW EXECUTE FUNCTION gis_package_guard();

-- ======================================================================
-- (3) Maquina de estados do pacote e revisao por outra pessoa
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_change_set_guard() RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'gis: pacote nasce em DRAFT (veio %)', NEW.status;
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT (
            (OLD.status = 'DRAFT' AND NEW.status IN ('SUBMITTED', 'CANCELLED'))
            OR (OLD.status = 'SUBMITTED' AND NEW.status IN ('IN_REVIEW', 'RETURNED', 'CANCELLED'))
            OR (OLD.status = 'IN_REVIEW' AND NEW.status IN ('APPROVED', 'APPLIED', 'RETURNED', 'REJECTED', 'CANCELLED'))
            OR (OLD.status = 'RETURNED' AND NEW.status IN ('SUBMITTED', 'CANCELLED'))
            OR (OLD.status = 'APPROVED' AND NEW.status = 'APPLIED')
        ) THEN
            RAISE EXCEPTION 'gis: transicao de pacote invalida: % -> %', OLD.status, NEW.status;
        END IF;
        IF NEW.status IN ('APPROVED', 'APPLIED') AND NEW.reviewed_by IS NOT NULL
           AND NEW.reviewed_by = OLD.author_id THEN
            RAISE EXCEPTION 'gis: autor nao aprova o proprio pacote (revisao por outra pessoa, GIS-04)';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_change_set_guard_trigger ON gis_change_set;
CREATE TRIGGER gis_change_set_guard_trigger BEFORE INSERT OR UPDATE ON gis_change_set
    FOR EACH ROW EXECUTE FUNCTION gis_change_set_guard();

CREATE OR REPLACE FUNCTION gis_change_set_review_guard() RETURNS TRIGGER AS $$
DECLARE
    v_author UUID;
    v_status TEXT;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    SELECT author_id, status INTO v_author, v_status
    FROM gis_change_set
    WHERE id = NEW.change_set_id AND tenant_id = NEW.tenant_id;
    IF v_author IS NULL THEN
        RAISE EXCEPTION 'gis: pacote % nao encontrado no tenant', NEW.change_set_id;
    END IF;
    IF NEW.reviewer_id = v_author THEN
        RAISE EXCEPTION 'gis: autor nao revisa o proprio pacote (revisao por outra pessoa, GIS-04)';
    END IF;
    IF v_status NOT IN ('SUBMITTED', 'IN_REVIEW') THEN
        RAISE EXCEPTION 'gis: pacote em % nao aceita revisao', v_status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_change_set_review_guard_trigger ON gis_change_set_review;
CREATE TRIGGER gis_change_set_review_guard_trigger BEFORE INSERT ON gis_change_set_review
    FOR EACH ROW EXECUTE FUNCTION gis_change_set_review_guard();

-- Item so entra em pacote aberto; pacote aplicado e imutavel.
CREATE OR REPLACE FUNCTION gis_change_set_item_guard() RETURNS TRIGGER AS $$
DECLARE
    v_status TEXT;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    IF TG_OP = 'DELETE' THEN
        -- Correcao M5: a imutabilidade prometida vale tambem para o
        -- DELETE — item de pacote fora de DRAFT/RETURNED nao se apaga.
        SELECT status INTO v_status FROM gis_change_set
        WHERE id = OLD.change_set_id AND tenant_id = OLD.tenant_id;
        IF v_status IS NULL THEN
            RAISE EXCEPTION 'gis: pacote % nao encontrado no tenant', OLD.change_set_id;
        END IF;
        IF v_status NOT IN ('DRAFT', 'RETURNED') THEN
            RAISE EXCEPTION 'gis: item de pacote em % nao pode ser apagado', v_status;
        END IF;
        RETURN OLD;
    END IF;
    SELECT status INTO v_status FROM gis_change_set
    WHERE id = NEW.change_set_id AND tenant_id = NEW.tenant_id;
    IF v_status IS NULL THEN
        RAISE EXCEPTION 'gis: pacote % nao encontrado no tenant', NEW.change_set_id;
    END IF;
    IF v_status NOT IN ('DRAFT', 'RETURNED') THEN
        -- Excecao do rebase (§6.3): em pacote submetido/em revisao, o
        -- autor pode atualizar SO a base do item (versao e snapshot),
        -- sem tocar no estado proposto. Correcao M5: a comparacao
        -- inclui change_set_id, tenant_id e validation_result — sem
        -- isso o "rebase" movia o item para outro pacote e reescrevia
        -- o resultado da validacao.
        IF TG_OP = 'UPDATE' AND v_status IN ('SUBMITTED', 'IN_REVIEW')
           AND NEW.change_set_id IS NOT DISTINCT FROM OLD.change_set_id
           AND NEW.tenant_id IS NOT DISTINCT FROM OLD.tenant_id
           AND NEW.operation IS NOT DISTINCT FROM OLD.operation
           AND NEW.feature_table IS NOT DISTINCT FROM OLD.feature_table
           AND NEW.feature_id IS NOT DISTINCT FROM OLD.feature_id
           AND NEW.proposed_state IS NOT DISTINCT FROM OLD.proposed_state
           AND NEW.proposed_geom_wkt IS NOT DISTINCT FROM OLD.proposed_geom_wkt
           AND NEW.proposed_geom_point_wkt IS NOT DISTINCT FROM OLD.proposed_geom_point_wkt
           AND NEW.validation_result IS NOT DISTINCT FROM OLD.validation_result THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'gis: itens so mudam em pacote DRAFT ou RETURNED (pacote em %)', v_status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_change_set_item_guard_trigger ON gis_change_set_item;
CREATE TRIGGER gis_change_set_item_guard_trigger BEFORE INSERT OR UPDATE OR DELETE ON gis_change_set_item
    FOR EACH ROW EXECUTE FUNCTION gis_change_set_item_guard();

-- Revisao e registro de auditoria: nao se apaga (correcao M5). Sem
-- esta guarda, um DELETE direto removia a decisao de um pacote
-- APPLIED, furando a imutabilidade que a guarda de item promete.
CREATE OR REPLACE FUNCTION gis_change_set_review_delete_guard() RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    RAISE EXCEPTION 'gis: revisao nao pode ser apagada (registro de auditoria do pacote %)',
        OLD.change_set_id;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_change_set_review_delete_guard_trigger ON gis_change_set_review;
CREATE TRIGGER gis_change_set_review_delete_guard_trigger BEFORE DELETE ON gis_change_set_review
    FOR EACH ROW EXECUTE FUNCTION gis_change_set_review_delete_guard();

-- Trava: pacote precisa estar aberto e locks ativos de pacotes distintos
-- nao se sobrepoem.
CREATE OR REPLACE FUNCTION gis_edit_lock_guard() RETURNS TRIGGER AS $$
DECLARE
    v_status TEXT;
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    SELECT status INTO v_status FROM gis_change_set
    WHERE id = NEW.change_set_id AND tenant_id = NEW.tenant_id;
    IF v_status IS NULL THEN
        RAISE EXCEPTION 'gis: pacote % nao encontrado no tenant', NEW.change_set_id;
    END IF;
    IF v_status NOT IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'RETURNED') THEN
        RAISE EXCEPTION 'gis: trava exige pacote aberto (pacote em %)', v_status;
    END IF;
    IF EXISTS (
        SELECT 1 FROM gis_edit_lock l
        WHERE l.tenant_id = NEW.tenant_id
          AND l.change_set_id IS DISTINCT FROM NEW.change_set_id
          AND l.expires_at > clock_timestamp()
          AND ST_Intersects(l.geom, NEW.geom)) THEN
        RAISE EXCEPTION 'gis: area ja travada por outro pacote';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_edit_lock_guard_trigger ON gis_edit_lock;
CREATE TRIGGER gis_edit_lock_guard_trigger BEFORE INSERT OR UPDATE ON gis_edit_lock
    FOR EACH ROW EXECUTE FUNCTION gis_edit_lock_guard();

-- ======================================================================
-- (4) Aplicacao do pacote numa so transacao (§6.2)
-- ======================================================================

-- Aplica um item sobre a feicao: UPDATE generico das colunas escalares de
-- proposed_state (tipos lidos do catalogo; geometria fora do JSONB, em
-- WKT), com change_set_id do pacote e version + 1. Colunas geradas, de
-- auditoria e a propria version nunca vem do proposed_state.
-- Correcao M1: (a) o schema vem EXPLICITO de quem chama
-- (gis_apply_change_set o resolve do tenant e fixa o search_path) —
-- nada de current_schema() aqui, que podia divergir e montar um UPDATE
-- vazio que "aplicava" so version + 1; (b) chave de proposed_state que
-- nao e coluna aplicavel da feicao RECUSA a aplicacao (erro de digitacao
-- nao vira no-op silencioso); (c) o tipo do cast e o format_type exato
-- do catalogo (dominio, varchar(n), numeric(p,s)), nao o udt_name.
CREATE OR REPLACE FUNCTION gis_apply_change_set_item(
    p_item gis_change_set_item, p_schema TEXT)
RETURNS VOID AS $$
DECLARE
    v_sets TEXT;
    v_sql TEXT;
    v_rows INT;
    v_unknown TEXT;
BEGIN
    SELECT string_agg(k, ', ') INTO v_unknown
    FROM jsonb_object_keys(p_item.proposed_state) AS k
    WHERE k IN ('id', 'tenant_id', 'geom', 'geom_point',
          'geom_web', 'length_m', 'slope', 'depth_m', 'version',
          'tenant_schema', 'created_at', 'created_by', 'updated_at',
          'change_set_id')
       OR NOT EXISTS (
          SELECT 1 FROM pg_attribute a
          JOIN pg_class t ON t.oid = a.attrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          WHERE n.nspname = p_schema AND t.relname = p_item.feature_table
            AND a.attname = k AND a.attnum > 0 AND NOT a.attisdropped);
    IF v_unknown IS NOT NULL THEN
        RAISE EXCEPTION 'gis: atributos propostos desconhecidos ou nao aplicaveis em %: %',
            p_item.feature_table, v_unknown;
    END IF;

    SELECT string_agg(
               format('%I = ', a.attname) ||
               CASE WHEN format_type(a.atttypid, a.atttypmod) = 'jsonb'
                    THEN format('($1 -> %L)', a.attname)
                    ELSE format('($1 ->> %L)::%s', a.attname,
                                format_type(a.atttypid, a.atttypmod))
               END, ', ')
    INTO v_sets
    FROM pg_attribute a
    JOIN pg_class t ON t.oid = a.attrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    JOIN (SELECT jsonb_object_keys(p_item.proposed_state) AS k) keys
      ON keys.k = a.attname
    WHERE n.nspname = p_schema
      AND t.relname = p_item.feature_table
      AND a.attnum > 0 AND NOT a.attisdropped;

    v_sql := format('UPDATE %I.%I f SET ', p_schema, p_item.feature_table)
        || COALESCE(v_sets || ', ', '')
        || 'change_set_id = $2, version = f.version + 1, '
        || 'updated_at = clock_timestamp()';
    IF p_item.proposed_geom_wkt IS NOT NULL THEN
        v_sql := v_sql || ', geom = ST_GeomFromText($5, 4674)';
    END IF;
    IF p_item.proposed_geom_point_wkt IS NOT NULL THEN
        v_sql := v_sql || ', geom_point = ST_GeomFromText($6, 4674)';
    END IF;
    v_sql := v_sql || ' WHERE f.id = $3 AND f.tenant_id = $4';
    EXECUTE v_sql USING p_item.proposed_state, p_item.change_set_id,
        p_item.feature_id, p_item.tenant_id, p_item.proposed_geom_wkt,
        p_item.proposed_geom_point_wkt;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows = 0 THEN
        RAISE EXCEPTION 'gis: feicao % nao encontrada em % para aplicar o item',
            p_item.feature_id, p_item.feature_table;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_apply_change_set(
    p_change_set_id UUID, p_reviewer_id UUID,
    p_warning_justification TEXT DEFAULT NULL)
RETURNS TEXT AS $$
DECLARE
    v_set gis_change_set;
    v_item gis_change_set_item;
    v_current_version BIGINT;
    v_lifecycle TEXT;
    v_sql TEXT;
    v_schema TEXT;
    v_tenant_guc UUID;
BEGIN
    -- Correcao A1/M1: resolve o schema do tenant (catalogo da
    -- plataforma pela GUC app.current_tenant; fallback: o schema
    -- corrente, que e o do tenant no caminho do migrador) e fixa o
    -- search_path da transacao nele antes de qualquer referencia sem
    -- qualificacao — o caminho da app nao fixa search_path (V13_5/6).
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    SELECT * INTO v_set FROM gis_change_set
    WHERE id = p_change_set_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'gis: pacote % nao encontrado', p_change_set_id;
    END IF;
    IF p_reviewer_id = v_set.author_id THEN
        RAISE EXCEPTION 'gis: autor nao aprova o proprio pacote (revisao por outra pessoa, GIS-04)';
    END IF;
    IF v_set.status NOT IN ('IN_REVIEW', 'APPROVED') THEN
        RAISE EXCEPTION 'gis: pacote em % nao pode ser aprovado/aplicado', v_set.status;
    END IF;
    IF EXISTS (SELECT 1 FROM gis_validation_issue
               WHERE tenant_id = v_set.tenant_id
                 AND change_set_id = v_set.id
                 AND status = 'OPEN' AND severity = 'ERROR') THEN
        RAISE EXCEPTION 'gis: pacote com problema de severidade erro nao pode ser aprovado';
    END IF;
    IF EXISTS (SELECT 1 FROM gis_validation_issue
               WHERE tenant_id = v_set.tenant_id
                 AND change_set_id = v_set.id
                 AND status = 'OPEN' AND severity = 'WARNING')
       AND (p_warning_justification IS NULL OR btrim(p_warning_justification) = '') THEN
        RAISE EXCEPTION 'gis: pacote com aviso exige justificativa do revisor';
    END IF;

    PERFORM set_config('app.gis_applying_change_set', v_set.id::text, true);

    FOR v_item IN
        SELECT * FROM gis_change_set_item
        WHERE change_set_id = v_set.id AND tenant_id = v_set.tenant_id
        ORDER BY created_at, id
    LOOP
        IF v_item.operation IN ('UPDATE', 'RETIRE') THEN
            v_sql := format('SELECT version FROM %I.%I WHERE id = $1 AND tenant_id = $2 FOR UPDATE',
                            v_schema, v_item.feature_table);
            EXECUTE v_sql INTO v_current_version USING v_item.feature_id, v_set.tenant_id;
            IF v_current_version IS NULL THEN
                RAISE EXCEPTION 'gis: feicao % nao encontrada em %',
                    v_item.feature_id, v_item.feature_table;
            END IF;
            IF v_current_version <> v_item.base_version THEN
                RAISE EXCEPTION 'gis: rebase exigido: feicao % mudou depois da versao de base % (atual %); o autor refaz o item sobre a versao atual',
                    v_item.feature_id, v_item.base_version, v_current_version;
            END IF;
        ELSIF v_item.operation = 'INSERT' THEN
            v_sql := format('SELECT lifecycle_status FROM %I.%I WHERE id = $1 AND tenant_id = $2 FOR UPDATE',
                            v_schema, v_item.feature_table);
            EXECUTE v_sql INTO v_lifecycle USING v_item.feature_id, v_set.tenant_id;
            IF v_lifecycle IS NULL THEN
                RAISE EXCEPTION 'gis: feicao nova % nao encontrada em %',
                    v_item.feature_id, v_item.feature_table;
            END IF;
            IF v_lifecycle <> 'PLANNED' THEN
                RAISE EXCEPTION 'gis: feicao nova % ja esta em %; item INSERT nao se aplica',
                    v_item.feature_id, v_lifecycle;
            END IF;
        END IF;
        PERFORM gis_apply_change_set_item(v_item, v_schema);
    END LOOP;

    IF v_set.status <> 'APPROVED' THEN
        INSERT INTO gis_change_set_review (tenant_id, change_set_id, decision, reviewer_id, reason)
        VALUES (v_set.tenant_id, v_set.id, 'APPROVED', p_reviewer_id, p_warning_justification);
    END IF;
    UPDATE gis_change_set
    SET status = 'APPLIED', reviewed_by = p_reviewer_id,
        reviewed_at = clock_timestamp(), applied_at = clock_timestamp(),
        updated_at = clock_timestamp()
    WHERE id = v_set.id;
    RETURN 'APPLIED';
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- (5) Rede na data (§3.7/§6.5) e pacotes aguardando revisao (P2.28)
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_network_at_recorded(p_at TIMESTAMPTZ)
RETURNS TABLE (feature_table TEXT, feature_id UUID, code TEXT,
               lifecycle_status TEXT, snapshot JSONB) AS $$
DECLARE
    v_table TEXT;
    v_id UUID;
    v_snap JSONB;
    v_schema TEXT;
    v_tenant_guc UUID;
BEGIN
    -- Correcao A1: fixa o search_path no schema do tenant (catalogo
    -- pela GUC app.current_tenant; fallback: schema corrente) — esta
    -- funcao e as de negocio abaixo chamam gis_feature_at (V13_2) e
    -- leem as tabelas de feicao sem qualificar, e o gis_feature_at
    -- tambem resolve pelo search_path. Sem STABLE: set_config nao
    -- combina com a promessa de estabilidade.
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    FOREACH v_table IN ARRAY ARRAY['gis_node', 'gis_arc', 'gis_connection',
                                   'gis_structure', 'gis_zone', 'gis_poi'] LOOP
        FOR v_id IN EXECUTE format(
            'SELECT id FROM %I UNION SELECT id FROM %I', v_table, v_table || '_history')
        LOOP
            v_snap := gis_feature_at(v_table, v_id, p_at);
            IF v_snap IS NOT NULL AND v_snap ->> 'lifecycle_status' = 'IN_SERVICE' THEN
                feature_table := v_table;
                feature_id := v_id;
                code := v_snap ->> 'code';
                lifecycle_status := v_snap ->> 'lifecycle_status';
                snapshot := v_snap;
                RETURN NEXT;
            END IF;
        END LOOP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Snapshot de negocio de uma feicao: primeiro a versao vigente no fim
-- da data pelo tempo do sistema; se a feicao so foi registrada depois
-- (carga retroativa do legado, comum na implantacao), vale a versao em
-- operacao mais recente cujo tempo do negocio cobre a data — sem isso,
-- rede instalada em 2024 e cadastrada em 2026 sumiria da consulta de
-- 2024, contra a definicao do §3.7.
-- JUSTIFICATIVA DO FALLBACK (correcao M2, contra o GIS-CADASTRO-
-- TECNICO §3.7): o §3.7 define a consulta de negocio pela JANELA
-- installed_on <= data < abandoned_on "com os atributos vigentes na
-- data pelo historico". O historico do modelo e por tempo do SISTEMA
-- (valid_period), nao por tempo do negocio: para feicao cadastrada
-- retroativamente (o caso normal da implantacao, GIS-01), nao existe
-- versao de historico cuja vigencia cubra a data de negocio, embora a
-- janela installed_on/abandoned_on diga que ela existia. O caminho
-- primario (gis_feature_at no fim da data) e o especificado e e o
-- preferido; o fallback so roda quando ele nao devolve nada, e mesmo
-- assim a EXISTENCIA na data continua decidida so pela janela do
-- §3.7 sobre a versao registrada mais recente — o que o fallback
-- aproxima sao os ATRIBUTOS (os da versao em operacao mais recente),
-- porque o modelo nao tem vigencia de negocio por atributo. Decisao
-- registrada aqui e no RELATORIO-P2.22.md; virar ADR ou nota formal no
-- GIS-CADASTRO-TECNICO e pendencia de integracao.
CREATE OR REPLACE FUNCTION gis_feature_business_snapshot(
    p_table TEXT, p_feature_id UUID, p_date DATE)
RETURNS JSONB AS $$
DECLARE
    v_at TIMESTAMPTZ := (p_date::timestamp + INTERVAL '1 day' - INTERVAL '1 second')::timestamptz;
    v_snap JSONB;
    v_hist TEXT := p_table || '_history';
    v_schema TEXT;
    v_tenant_guc UUID;
BEGIN
    -- Correcao A1: idem gis_network_at_recorded (search_path do tenant).
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    v_snap := gis_feature_at(p_table, p_feature_id, v_at);
    IF v_snap IS NOT NULL THEN
        RETURN v_snap;
    END IF;
    -- Carga retroativa: installed_on/abandoned_on autoritativos sao os
    -- da versao registrada mais recente; a janela do negocio decide a
    -- existencia na data, e o snapshot devolvido e a versao em operacao
    -- mais recente (atributos).
    EXECUTE format('SELECT to_jsonb(f) FROM %I f WHERE f.id = $1', p_table)
        INTO v_snap USING p_feature_id;
    IF v_snap IS NULL THEN
        EXECUTE format(
            'SELECT to_jsonb(h) FROM %I h WHERE h.id = $1'
            || ' ORDER BY lower(h.valid_period) DESC LIMIT 1', v_hist)
            INTO v_snap USING p_feature_id;
    END IF;
    IF v_snap IS NULL
       OR (v_snap ->> 'installed_on') IS NULL
       OR (v_snap ->> 'installed_on')::date > p_date
       OR ((v_snap ->> 'abandoned_on') IS NOT NULL
           AND (v_snap ->> 'abandoned_on')::date <= p_date) THEN
        RETURN NULL;
    END IF;
    EXECUTE format(
        'SELECT cand FROM ('
        || '  SELECT to_jsonb(h) AS cand, lower(h.valid_period) AS ord FROM %I h WHERE h.id = $1'
        || '  UNION ALL'
        || '  SELECT to_jsonb(f) AS cand, f.created_at AS ord FROM %I f WHERE f.id = $1'
        || ') c WHERE cand ->> ''lifecycle_status'' = ''IN_SERVICE'''
        || ' ORDER BY ord DESC LIMIT 1', v_hist, p_table)
        INTO v_snap USING p_feature_id;
    RETURN v_snap;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_network_at_business(p_date DATE)
RETURNS TABLE (feature_table TEXT, feature_id UUID, code TEXT,
               lifecycle_status TEXT, snapshot JSONB) AS $$
DECLARE
    v_table TEXT;
    v_id UUID;
    v_snap JSONB;
    v_schema TEXT;
    v_tenant_guc UUID;
BEGIN
    -- Correcao A1: idem gis_network_at_recorded (search_path do tenant).
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    FOREACH v_table IN ARRAY ARRAY['gis_node', 'gis_arc', 'gis_connection',
                                   'gis_structure', 'gis_zone', 'gis_poi'] LOOP
        FOR v_id IN EXECUTE format(
            'SELECT id FROM %I UNION SELECT id FROM %I', v_table, v_table || '_history')
        LOOP
            v_snap := gis_feature_business_snapshot(v_table, v_id, p_date);
            IF v_snap IS NOT NULL
               AND v_snap ->> 'lifecycle_status' = 'IN_SERVICE'
               AND (v_snap ->> 'installed_on') IS NOT NULL
               AND (v_snap ->> 'installed_on')::date <= p_date
               AND ((v_snap ->> 'abandoned_on') IS NULL
                    OR (v_snap ->> 'abandoned_on')::date > p_date) THEN
                feature_table := v_table;
                feature_id := v_id;
                code := v_snap ->> 'code';
                lifecycle_status := v_snap ->> 'lifecycle_status';
                snapshot := v_snap;
                RETURN NEXT;
            END IF;
        END LOOP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Pacotes aguardando revisao: a superficie que a P2.28 (minhas tarefas,
-- PLT-13) consome como item de tarefa do revisor. A fila/tela da P2.28
-- nao e desta tarefa; aqui ficam o estado e a consulta.
CREATE OR REPLACE VIEW gis_change_set_pending_review AS
SELECT cs.id AS change_set_id, cs.tenant_id, cs.type, cs.channel,
       cs.status, cs.author_id, cs.submitted_at, cs.created_at,
       (SELECT count(*) FROM gis_change_set_item i
         WHERE i.change_set_id = cs.id AND i.tenant_id = cs.tenant_id) AS item_count
FROM gis_change_set cs
WHERE cs.status IN ('SUBMITTED', 'IN_REVIEW');

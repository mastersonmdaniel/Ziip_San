-- ED-99 Transparencia ativa, LAI e dados abertos — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi confirmado livre para esta frente (na main, a historia
-- tenant do core vai ate V21_3 e pacotes locais pendentes — ED-35 entre eles —
-- disputam os proximos slots); NAO inventar numero: na integracao, atribuir o
-- proximo slot livre da historia do modulo shared/ziipsan-core, renomear o
-- arquivo e conferir FKs contra V8_1 (service_protocols) e o catalogo de
-- parametros (definitions de pub.*/sac.lai_deadlines contra V9_9).
-- Desenho:
--  * pub_lai_requests persiste o pedido nativo (pub.lai_mode=NATIVE_SIC) com
--    FK real para o protocolo da P1.36 no padrao do repo:
--    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
--    (mesmo padrao de V21_2 e da ED-35). NENHUMA coluna de requerente: a
--    identidade vive no protocolo; relatorio anual e dados abertos sao
--    agregados por construcao.
--  * Os prazos gravados sao o INSTANTANEO de sac.lai_deadlines usado na
--    abertura, com CHECKs nos tetos da Lei 12.527/2011 da spec
--    (resposta <= 20, prorrogacao <= 10, recurso <= 10, decisao <= 5):
--    a guarda de classe c existe no codigo (LaiDeadlines) E no banco.
--  * pub_transparency_items persiste os itens da transparencia ativa
--    (seis secoes da spec); a secao INDICATORS nao guarda valor aqui —
--    o valor e lido da ED-48/P3.41 na fiacao, sem segunda fonte.
--  * pub_open_data_datasets registra os conjuntos publicados (metadado);
--    o conteudo exportado e gerado pela fiacao a partir de dados agregados.

CREATE TABLE IF NOT EXISTS pub_lai_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('COUNTER','PHONE','PORTAL','APP','EMAIL','WHATSAPP','REGULATOR','OMBUDSMAN')),
    opened_date DATE NOT NULL,
    response_days INT NOT NULL CHECK (response_days BETWEEN 1 AND 20),
    extension_days INT NOT NULL CHECK (extension_days BETWEEN 1 AND 10),
    appeal_days INT NOT NULL CHECK (appeal_days BETWEEN 1 AND 10),
    appeal_decision_days INT NOT NULL CHECK (appeal_decision_days BETWEEN 1 AND 5),
    original_response_due DATE NOT NULL,
    current_response_due DATE NOT NULL,
    extended BOOLEAN NOT NULL DEFAULT FALSE,
    extension_justification VARCHAR(500),
    answered_date DATE,
    answer_outcome VARCHAR(20) CHECK (answer_outcome IN ('GRANTED','PARTIALLY_GRANTED','DENIED')),
    appeal_date DATE,
    appeal_decision_due DATE,
    appeal_decided_date DATE,
    appeal_outcome VARCHAR(10) CHECK (appeal_outcome IN ('GRANTED','DENIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (current_response_due >= original_response_due),
    CHECK (extended = FALSE OR NULLIF(btrim(extension_justification),'') IS NOT NULL),
    CHECK (answered_date IS NULL OR answered_date >= opened_date),
    CHECK (answered_date IS NULL OR answer_outcome IS NOT NULL),
    CHECK (appeal_date IS NULL OR answered_date IS NOT NULL),
    CHECK (appeal_date IS NULL OR appeal_date >= answered_date),
    CHECK (appeal_date IS NULL OR answer_outcome IN ('DENIED','PARTIALLY_GRANTED')),
    CHECK (appeal_decided_date IS NULL OR appeal_date IS NOT NULL),
    CHECK (appeal_decided_date IS NULL OR appeal_decided_date >= appeal_date),
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES service_protocols(tenant_id, id)
);
CREATE TABLE IF NOT EXISTS pub_transparency_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    section VARCHAR(20) NOT NULL CHECK (section IN ('COMPETENCIES','SERVICE_CHANNELS','TARIFFS','CONTRACTS','SERVICE_CHARTER')),
    title VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(title),'') IS NOT NULL),
    reference VARCHAR(500) NOT NULL CHECK (NULLIF(btrim(reference),'') IS NOT NULL),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS pub_open_data_datasets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    dataset_name VARCHAR(120) NOT NULL CHECK (NULLIF(btrim(dataset_name),'') IS NOT NULL),
    format VARCHAR(10) NOT NULL CHECK (format IN ('CSV','JSON')),
    fields TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, dataset_name)
);
SELECT register_tenant_table('pub_lai_requests');
SELECT register_tenant_table('pub_transparency_items');
SELECT register_tenant_table('pub_open_data_datasets');
-- Definitions dos parametros da ED-99 (core parameter_definitions; conferir o
-- formato exato das colunas contra V9_9 na integracao):
--   pub.lai_mode: ENUM NONE/EXTERNAL_LINK/NATIVE_SIC; padrao por
--     natureza juridica (AUTARCHY/PUBLIC_COMPANY -> EXTERNAL_LINK; demais -> NONE)
--   pub.lai_external_url: TEXT (URL), obrigatoria com EXTERNAL_LINK
--   pub.falabr_opt_in: BOOLEAN, padrao false (adesao voluntaria, GES-06)
--   pub.open_data_enabled: BOOLEAN, padrao false
--   (classe dos parametros pub.* NAO especificada na spec da ED-99 —
--    atribuicao removida na correcao pos-revisao, achado D3; confirmar
--    a classe com fonte na integracao. So sac.lai_deadlines tem classe
--    fixada na spec.)
--   sac.lai_deadlines: OBJECT {responseDays, extensionDays, appealDays,
--     appealDecisionDays}, classe c (so reduz), padrao de fabrica = tetos da
--     Lei 12.527/2011 da spec (20/10/10/5) — NUNCA superior a lei

-- Repeatable Migration for Tenant Schema
-- Description: P0.11 — seed do catálogo data_classification a partir das regras de redação.
--
-- Por que repetível e não só na V1_6: as regras de redação chegam em migrations de
-- vários módulos, inclusive posteriores à V1_6 (V1_10, V2_6–V2_10, V3_2–V3_4, V5_2). A
-- repetível roda depois de todas as versionadas num provisionamento novo e, por isso,
-- classifica todas as regras existentes. Uma tarefa futura que crie coluna com dado
-- pessoal precisa classificar a coluna na própria migration (ou aqui): o teste
-- DataClassificationCoverageTest recusa coluna nova sem classificação.
--
-- Classes: ID identificação (nome, documento, índice, assinatura, registro
-- profissional, texto livre que pode conter dado pessoal), CT contato (e-mail,
-- telefone), LOC localização (endereço, CEP, coordenadas), FIN financeiro, SOC
-- social/demográfico, SENS sensível (LGPD art. 11 — hoje, só fotos que podem mostrar
-- pessoas). FIN e SOC ainda não têm coluna classificada: nenhuma regra de redação
-- atual é financeira ou social, e inventar uma seria fabricar dado.
--
-- A tabela é criada aqui também (IF NOT EXISTS) para a repetível ser autocontida; a
-- V1_6 já a cria no caminho versionado.

CREATE TABLE IF NOT EXISTS public.data_classification (
    table_name VARCHAR(100) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    class VARCHAR(10) NOT NULL
        CHECK (class IN ('ID', 'CT', 'LOC', 'FIN', 'SOC', 'SENS')),
    reason TEXT NOT NULL,
    PRIMARY KEY (table_name, column_name)
);

-- Toda coluna com regra de redação é dado pessoal e ganha classificação, derivada da
-- regra (o motivo vem da própria regra, sem inventar descrição nova).
INSERT INTO public.data_classification (table_name, column_name, class, reason)
SELECT r.table_name, r.column_name,
       CASE
           WHEN r.column_name IN ('cep', 'street', 'number', 'complement', 'address',
                                  'coordinates', 'geolocation') THEN 'LOC'
           WHEN r.column_name = 'photos' THEN 'SENS'
           WHEN r.table_name = 'customer_contacts' AND r.column_name = 'value' THEN 'CT'
           WHEN r.column_name = 'email' THEN 'CT'
           ELSE 'ID'
       END,
       r.reason
FROM public.audit_redaction_rule r
ON CONFLICT (table_name, column_name) DO NOTHING;

-- Colunas do próprio registro de acesso (sem trigger de auditoria, logo sem regra):
-- o índice cego e o usuário do acesso são identificadores.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('personal_data_access_log', 'document_hash', 'ID', 'índice cego do documento buscado/revelado'),
    ('personal_data_access_log', 'user_id', 'ID', 'usuário que acessou o dado pessoal')
ON CONFLICT (table_name, column_name) DO NOTHING;

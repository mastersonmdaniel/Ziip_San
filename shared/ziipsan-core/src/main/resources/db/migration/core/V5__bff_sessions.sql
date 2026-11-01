-- Core Migration — Version: 5.0.0
-- Description: P0.14 — tabelas do Spring Session JDBC (sessao do BFF) em public.
--
-- As sessoes do navegador vivem no PostgreSQL, nao na memoria do processo: duas
-- replicas da api leem a mesma base e nenhuma afinidade de sessao e necessaria.
-- As tabelas seguem o schema que o spring-session-jdbc espera (SPRING_SESSION /
-- SPRING_SESSION_ATTRIBUTES; o PostgreSQL dobra identificadores nao citados para
-- minusculas, por isso os nomes aqui ja estao em minusculas).
--
-- Sem RLS de tenant, de proposito: a sessao e do servidor, anterior a qualquer
-- tenant (o tenant so existe depois que o token do usuario e reconstruido). O
-- acesso e so pela role da aplicacao (ziipsan_app), via GRANT abaixo; nenhum
-- outro papel recebe acesso, e a api nao tem DDL para criar estas tabelas em
-- runtime (spring.session.jdbc.initialize-schema=never em application.yml).
-- Entra pelo job migrate da P0.6 (CoreMigrationRunner), nunca na subida da api.

CREATE TABLE IF NOT EXISTS spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id),
    CONSTRAINT spring_session_ix UNIQUE (session_id)
);
CREATE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (expiry_time);
CREATE INDEX IF NOT EXISTS spring_session_ix2 ON spring_session (principal_name);

CREATE TABLE IF NOT EXISTS spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session_attributes TO ziipsan_app;

-- P0.16 — baldes do rate limit (uma linha por chave, janela fixa). Tabela do
-- servidor, em public e sem RLS de tenant, como as de sessão: o limite é
-- aplicado antes de qualquer decisão de tenant e vale entre réplicas (o
-- compartilhamento é pelo banco, sem Redis). A janela antiga é sobrescrita
-- pela própria instrução de incremento (INSERT ... ON CONFLICT), sem
-- limpeza periódica. Entra pelo mesmo job migrate da P0.6.
CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    bucket_key VARCHAR(200) NOT NULL,
    window_start BIGINT NOT NULL,
    request_count INT NOT NULL,
    CONSTRAINT rate_limit_buckets_pk PRIMARY KEY (bucket_key)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON rate_limit_buckets TO ziipsan_app;

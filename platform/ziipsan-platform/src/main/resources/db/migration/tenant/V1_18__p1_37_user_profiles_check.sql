-- Tenant Migration (platform)
-- Version: 1.18.0
-- Description: P1.37 (integracao P1-C) — alarga o CHECK de user_profiles.profile
--   para os 26 perfis de fabrica.
--
-- POR QUE EXISTE: a V1_9 criou user_profiles com CHECK (profile IN
-- ('ADMIN','MANAGER','OPERATOR','VIEWER')) — os quatro minimos da P0.13.
-- A P1.37 criou os 22 perfis por funcao no codigo (PermissionCatalog) e a
-- resolucao por token ja funciona para os 26, mas gravar uma atribuicao de
-- perfil novo em user_profiles falhava no banco com 23514 (violacao do
-- CHECK). Sem esta migration, o administrador do tenant nao conseguiria
-- atribuir nenhum perfil por funcao pela API.
--
-- VERSAO: V1_18 e a primeira livre do historico platform-tenant apos a
-- V1_17 (P1.33, visoes salvas). V1_12–V1_15 seguem reservadas no §3 do plano
-- para P2/P3 (ver o cabecalho da V1_17). O nome da constraint e o que o
-- Postgres gera para o CHECK inline da V1_9 (tabela_coluna_check); o DROP
-- usa IF EXISTS para nao quebrar se a constraint ja tiver outro nome em
-- alguma base (nesse caso o ADD abaixo falharia e o erro apareceria — nao
-- se finge sucesso).
--
-- A lista abaixo espelha exatamente PermissionCatalog.FactoryProfile (26
-- codigos, todos com ate 20 caracteres, limite da coluna): nenhum codigo
-- inventado aqui; a tarefa que criar um perfil novo atualiza esta lista
-- numa migration propria, nunca editando esta.
ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS user_profiles_profile_check;

ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_profile_check CHECK (profile IN (
    'ADMIN', 'MANAGER', 'OPERATOR', 'VIEWER',
    'ATENDENTE', 'SUPERVISOR_COMERCIAL', 'FATURISTA', 'ANALISTA_ARRECADACAO',
    'LEITURISTA', 'PROGRAMADOR_OS', 'EXECUTOR_CAMPO', 'ALMOXARIFE',
    'OPERADOR_ESTACAO', 'ANALISTA_PERDAS', 'LABORATORISTA', 'RESP_TEC_QUALIDADE',
    'EDITOR_CAD_TECNICO', 'REVISOR_CAD_TECNICO', 'GESTOR', 'AUDITOR_INTERNO',
    'OUVIDOR', 'ENCARREGADO_DADOS', 'PROCURADOR', 'CONTADOR',
    'AUDITOR_EXTERNO', 'REGULADOR'
));

-- ED-15 -- definicoes dos parametros read.* do ciclo de leitura no catalogo global (core).
--
-- *** PENDENTE DE VERSAO ***
-- O aceite da ED-15 nomeia as tres chaves abaixo, mas nem a tarefa nem o
-- mapa de migrations do plano v3.0 (secao 3) reservam versao Flyway para
-- a ED-15. Por isso este arquivo NAO esta em db/migration/ e nenhum
-- numero foi inventado: na integracao, ele entra como subversao da
-- reserva vigente do core (precedente ED-14/ED-17, SQL em db/pendente/).
--
-- NENHUM factory_default e gravado: o aceite nao fixa valor de fabrica
-- para nenhuma das tres chaves. Sem valor vigente do tenant, as regras
-- da ED-15 falham com erro explicito (principio do PARAMETROS-DO-TENANT)
-- em vez de presumir percentual, foto ou tentativas.
-- A classe (param_class) tambem nao e fixada aqui pelo mesmo motivo:
-- o aceite da ED-15 nao classifica as chaves; a classificacao entra na
-- integracao com a decisao correspondente, e a coluna fica NULL ate la
-- apenas neste rascunho pendente -- conferir a obrigatoriedade da coluna
-- no catalogo vigente antes de aplicar.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('read.resample_pct', 'PERCENT', NULL, NULL,
     'Aceite da ED-15', NULL,
     'Percentual da releitura amostral: fracao das leituras de cada rota que volta por OS seletiva de releitura (selecao sistematica deterministica).'),
    ('read.photo_required_on', 'TEXT', NULL, NULL,
     'Aceite da ED-15', NULL,
     'Quando a foto e obrigatoria no ciclo de leitura: IMPEDIMENT_ONLY (so no impedimento) ou EVERY_READING (em toda leitura).'),
    ('read.impediment_attempts', 'INT', NULL, NULL,
     'Aceite da ED-15', NULL,
     'Numero de tentativas de acesso exigido para aceitar um impedimento (>= 1).')
ON CONFLICT (definition_key) DO NOTHING;

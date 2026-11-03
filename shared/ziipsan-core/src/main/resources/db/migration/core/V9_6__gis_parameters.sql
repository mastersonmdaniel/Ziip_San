-- Core Migration — Version: 9.6
-- Description: P1.26 (GIS-01) — definicoes dos parametros gis.* e seus limites
-- (GIS-CADASTRO-TECNICO §3.10; ADR-0034).
--
-- gis.work_srid aceita SO a lista do §3.9, registrada abaixo como ALLOWED_VALUES:
-- fusos 18S a 25S (EPSG 31978 a 31985), 18N a 22N (31972 a 31976) e 26S (5396).
-- A validacao do valor do tenant contra esta lista e do mecanismo de parametros
-- (P1.6, console); o catalogo aqui e o registro da lista, nao a validacao em si.
--
-- gis.snap_tolerance_m e gis.import_snap_tolerance_m trazem valores de referencia
-- a validar (o proprio §3.10 os marca assim): o piso/teto registrado nao e norma
-- conferida, porque as normas da P1.28 nao foram compradas (sem aprovacao do dono).
--
-- gis.public_aggregation_min_units: piso 5, classe c (so amplia), decisao do dono
-- do produto (plano v2.11). Ja existia em PARAMETROS-DO-TENANT; aqui entra no
-- catalogo global com o FLOOR correspondente.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('gis.work_srid', 'ENUM', 'd', NULL,
     'GIS-CADASTRO-TECNICO §3.9 (SIRGAS 2000 / UTM)',
     NULL,
     'SRID UTM de trabalho do tenant. So a lista do §3.9: fusos 18S a 25S, 18N a 22N e 26S. Municipio que cruza fuso usa o fuso da sede, com a escolha registrada.'),
    ('gis.vertical_datum', 'ENUM', 'd', '"IMBITUBA"',
     'GIS-CADASTRO-TECNICO §3.9 (REALT-2018)',
     NULL,
     'Datum vertical do tenant: Imbituba (Santana no Amapa). Altitude elipsoidal do GNSS so em transito, antes da conversao pelo hgeoHNOR2020 no servidor.'),
    ('gis.snap_tolerance_m', 'DECIMAL', 'b', '0.10',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)',
     NULL,
     'Tolerancia de snap na edicao, em metros. Padrao 0,10 (valor de referencia a validar, nao norma conferida).'),
    ('gis.import_snap_tolerance_m', 'DECIMAL', 'b', '0.50',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)',
     NULL,
     'Tolerancia padrao de snap na importacao em lote, em metros. Padrao 0,50 (valor de referencia a validar); o lote pode reduzir.'),
    ('gis.service_line_max_distance_m', 'INT', 'b', '100',
     'GIS-CADASTRO-TECNICO §3.10 (contrato de Nova Trento, 19a §2.2)',
     NULL,
     'Distancia maxima do ramal automatico, em metros.'),
    ('gis.accuracy_targets', 'TABLE', 'b', '{"h_m": 0.10, "v_m": 0.05}',
     'GIS-CADASTRO-TECNICO §3.8, decisao 5 do ADR-0034',
     NULL,
     'Meta de exatidao por classe: padrao de fabrica ±0,10 m na horizontal e ±0,05 m na vertical. O tenant altera por classe, com vigencia e aprovacao.'),
    ('gis.basemap_provider', 'ENUM', 'd', '"OPENSTREETMAP"',
     'GIS-CADASTRO-TECNICO §3.10',
     NULL,
     'Mapa base padrao. Google e Bing so com licenca no contrato.'),
    ('gis.style_profile', 'ENUM', 'b', '"ZIIP"',
     'GIS-CADASTRO-TECNICO §3.10',
     NULL,
     'Perfil de simbologia: padrao Ziip, com override por tipo e estado.'),
    ('gis.public_aggregation_min_units', 'INT', 'c', '5',
     'Decisao do dono do produto, plano v2.11 (ADR-0034, ajuste v2.11, item 5)',
     'DPO',
     'Minimo de imoveis por celula (quadra ou setor) em publicacao fora do backoffice autenticado. Piso 5, so amplia. Celula abaixo do minimo nao e publicada; nenhum ponto de imovel ou ligacao fora do backoffice.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('gis.work_srid', 'ALLOWED_VALUES',
     '["31972", "31973", "31974", "31975", "31976", "31978", "31979", "31980", "31981", "31982", "31983", "31984", "31985", "5396"]',
     'GIS-CADASTRO-TECNICO §3.9', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Fusos 18N a 22N (31972 a 31976), 18S a 25S (31978 a 31985) e 26S (5396, Trindade e Martim Vaz; conferir no spatial_ref_sys da versao em uso).'),
    ('gis.vertical_datum', 'ALLOWED_VALUES', '["IMBITUBA", "SANTANA"]',
     'GIS-CADASTRO-TECNICO §3.9', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Imbituba em geral; Santana no Amapa. ELLIPSOIDAL e UNKNOWN existem como valores de dado (coluna vertical_datum da feicao), nao como padrao do tenant.'),
    ('gis.snap_tolerance_m', 'MIN_VALUE', '0.01',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Limite do §3.10, marcado la como valor de referencia a validar.'),
    ('gis.snap_tolerance_m', 'MAX_VALUE', '1.00',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Limite do §3.10, marcado la como valor de referencia a validar.'),
    ('gis.import_snap_tolerance_m', 'MIN_VALUE', '0.01',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Limite do §3.10, marcado la como valor de referencia a validar.'),
    ('gis.import_snap_tolerance_m', 'MAX_VALUE', '5.00',
     'GIS-CADASTRO-TECNICO §3.10 (valor de referencia a validar)', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Limite do §3.10, marcado la como valor de referencia a validar.'),
    ('gis.service_line_max_distance_m', 'MIN_VALUE', '1',
     'GIS-CADASTRO-TECNICO §3.10', NULL,
     FALSE, FALSE, FALSE, FALSE, NULL),
    ('gis.service_line_max_distance_m', 'MAX_VALUE', '300',
     'GIS-CADASTRO-TECNICO §3.10', NULL,
     FALSE, FALSE, FALSE, FALSE, NULL),
    ('gis.public_aggregation_min_units', 'FLOOR', '5',
     'Decisao do dono do produto, plano v2.11 (ADR-0034, ajuste v2.11, item 5)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Piso 5, classe c: o tenant so pode ampliar, nunca reduzir. Vale para qualquer dado agregado de imovel ou ligacao fora do backoffice autenticado.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;

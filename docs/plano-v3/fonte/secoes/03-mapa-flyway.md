## 3. Mapa de versões Flyway

Duas histórias: **core** (`public`, uma vez por banco) e **tenant** (uma por schema). Existentes, conferidas em
`**/db/migration/**` em 12/10/2026: core `V1`, `V2`; tenant `V1_1`–`V1_4`, `V2_1`–`V2_10`, `V3_1`–`V3_3`, `V4_1`, `V5_1`,
`V5_2`, `V6_1`.

**Regra de ordem.**
- Core nunca usa `outOfOrder`: o número sai na ordem do merge; a tabela mostra a ordem esperada e é corrigida se ela mudar.
- Tenant: faixa por módulo com `outOfOrder(true)` até o fim do P3; até lá todo tenant é recriável (massa sintética ou
  reimportação do legado, que segue como fonte da verdade). Na P3.12, antes do paralelo, `outOfOrder(false)`; daí em
  diante a versão vem de uma sequência global a partir de `V100`, com o módulo no nome (`V100__dunning_actions.sql`), e o
  CI recusa versão menor ou igual à maior publicada. Reservas não usadas até lá são renumeradas aqui.
- Expand/contract: coluna só sai na release seguinte à que deixou de usá-la.
- Subversões do core: para não renumerar reservas, tabelas novas do core entram como subversão da reserva da mesma
  fase (core `V5_1`, `V9_1`…), que o Flyway ordena entre as inteiras. O prefixo "core" as distingue das versões de
  tenant de mesmo número (por exemplo, tenant `V9_1`, field), que ficam em outra tabela de histórico.
- A coluna "Módulo" com `platform` designa o módulo Maven `platform/*-platform` (história de tenant). Os schemas
  `platform` e `support` do banco do core (ADR-0018, ADR-0020) entram pelas versões do core. Só o banco principal, o
  do catálogo, os usa; nos clusters adicionais (ADR-0005) ficam vazios, salvo decisão diferente na P1.14.
- Renomeação (P0.33): muda o checksum das migrations existentes, porque os `GRANT` citam as roles; aceito porque não
  há banco com dado real, e banco e Keycloak de desenvolvimento são recriados. Depois dela, as migrations voltam a ser
  imutáveis.

**Mapa da v1, corrigido pelo que foi aplicado**

| Versão | Módulo | Conteúdo | Situação |
|---|---|---|---|
| core `V1`, `V2`; `V1_1`–`V1_4` | core, platform | catálogo e `audit_log`; redação; schema do tenant; auditoria endurecida | existentes |
| `V1_5` | platform | parâmetros do tenant; `holidays` sai do `V6_1` | reservada, P0 (era R4) |
| `V2_1`–`V2_10` | quality, losses, gis, mtr, lgpd | schemas; redação; cifra do MTR em `V2_8` (a v1 previa `V2_6`) | existentes |
| `V3_1`–`V3_3` | registry | schema; redação (a v1 reservava `V3_3` para o log de acesso, que vai para `V1_6`) | existentes |
| `V4_1`; `V5_1`, `V5_2`; `V6_1` | billing, metering, collection | schemas; `V5_2` é redação (tarefas de leitura vão para `V5_3`) | existentes |
| `V4_2` / `V4_3`, `V4_4` | billing | tarifa social / ciclo, modelos de conta | reservadas, P1 / P2 |
| `V4_5` | `sigsan-fiscal` | tributos com vigência (CBS/IBS) e motor fiscal: certificados, séries, documentos, eventos, lotes | reservada, P1 |
| `V6_2`; `V8_1`, `V9_2` | collection; portal, field | conciliação, Pix; protocolo; bloqueios de corte (a OS passa à `V9_1`, P1.31, v2.14); seleção mínima do aviso, prova de entrega e motivos de não geração em `V9_2` (v2.15) | reservadas, P3 |
| `V7_x`, `V10_x`, `V12_x`, `V13_x` | integration, dunning, GRS, GIS | SINISA, e-Sfinge e conectores de ERP (ED-33, ED-62); cobrança, negativação e protesto (P4.1, ED-30) e cobrança por empresa terceirizada (ED-68, v2.15); DMR; GIS: núcleo em `V13_1` a `V13_8` no P1–P3 (reservas novas, abaixo, v2.10), trilha (ED-53 a ED-56, ED-63 a ED-67) e incidente pela sequência global | núcleo do GIS no P1–P3; P4 (trilha de editais) e 2028, sequência global |
| `V11_x` | events | outbox por tenant | substituída por core `V8` (ADR-0014: uma outbox por banco) |

**Reservas novas**

| Versão | Módulo | Conteúdo | Tarefa |
|---|---|---|---|
| core `V3` | core | `parameter_definitions`, `parameter_legal_limits` (catálogo global do produto, só leitura para a aplicação) | P0.19 |
| core `V4` | core | `tenant_catalog.cluster_id`, `isolation_mode`; revisão do `schema_name UNIQUE` (`core/V1:21`); `tenant_schema_version`; `sigsan_app` só com SELECT | P0.17, P0.21, P1.14 |
| core `V5` | core | sessões do BFF | P0.14 |
| core `V5_1` | core | schema `platform`; `platform.deployment` (registro de implantação), gravada pelo `migrate` (ADR-0021) | P0.42 |
| core `V6` | core | `tenant_key` (DEK por tenant). Era "core `V3`" no R1 da v1: o R1 depende da hospedagem e o core não aceita fora de ordem | P0.28 |
| core `V7` | core | `audit_log` particionado por mês, PK `(created_at, id)` com UUIDv7, retenção | P1.7 |
| core `V8` | core | `outbox` com `tenant_id`, RLS e partição diária | P1.9 |
| core `V9` | core | console v0 no schema `platform`: `plan`, `tenant_subscription`, `feature_flag`, `onboarding_step`, `tenant_lifecycle`, `audit_event`. A impersonação sai: o acesso assistido do ADR-0020 vai para o core `V10_3` | P1.6 |
| core `V9_1` | core | Central v0: `platform.metric_definition`, `metric_point` (partição diária), `metric_daily`, `alert_rule`, `alert_event`, `config_item`; role `ziipsan_central`; funções `platform_stats.*` | P1.17 |
| core `V9_2` | core | `public.tenant_assets` (logo do tenant, com estado da varredura); função `SECURITY DEFINER` da marca pré-login, restrita às chaves `branding.*` de `tenant_settings` | P1.19 |
| core `V9_3` | core | schema `support`; conteúdo de ajuda: `article`, `article_version`, `article_feedback`, `tour_completion`, `error_explanation` | P1.21 |
| core `V9_4` | core | `support.ticket`, `ticket_message`, `ticket_attachment`, `ticket_event`; sequência do protocolo `SUP-AAAA-NNNNNN` | P1.23 |
| core `V9_5` | core | `platform.document_acceptance`; `support.release_note_view` | P1.24 |
| core `V9_6` | core | definições dos parâmetros `gis.*` em `parameter_definitions` e limites em `parameter_legal_limits` (SRID de trabalho, datum vertical, tolerâncias de snap, distância do ramal automático, metas de precisão, mapa base, perfil de simbologia, piso de agregação) (ADR-0034) | P1.26 |
| core `V9_7` | core | `platform.vertical` (id, versão, estado) e `platform.tenant_vertical` (tenant, vertical, ativação, estado); todo tenant existente nasce com `sanitation` ativo | P0.47 |
| core `V9_8` | core | catálogo global de normas: `platform.cert_standard`, `cert_standard_edition` (com vigência futura) e `cert_clause` (número de cláusula e mapa em palavras próprias, sem texto da norma) | P1.32 |
| core `V10` | core | `job_runs` e travas por (tenant, ciclo) | P2.1 |
| core `V10_1` | core | contrato SaaS no schema `platform`: contrato (com a política de SLA), aditivo, empenho, reajuste, fatura, pagamento, referências aos documentos da fatura e da NFS-e | P2.12–P2.14 |
| core `V10_2` | core | `support.sla_policy` (com as cinco severidades do nível Setor Público, ADR-0025: prazo de solução, aderência e multa; matriz impacto × criticidade), `csat_response`; prazos e pausa no chamado | P2.15 |
| core `V10_3` | core | `support.access_consent`, `assisted_access_session` (tipo `CONSENTED` ou `EMERGENCY`; na emergência, referência ao incidente no lugar do consentimento) | P3.17 |
| core `V10_4` | core | `support.status_component`, `status_incident`, `status_update`, `maintenance_window` | P3.18 |
| core `V10_5` | core | período de consulta somente leitura pós-contrato no ciclo de vida do tenant (início, fim, usuários), antes da carência (ADR-0025) | P3.16 |
| core `V11` | core | instância dedicada no catálogo (modo de isolamento e ambiente, ADR-0024); configuração do app do cliente por tenant (identificador do app, contas de loja, versão publicada, ADR-0026) | ED-04, ED-39 |
| core `V11_1` | core | `platform.app_device`: dispositivos do app de campo por tenant (estado `ACTIVE`, `BLOCKED` ou `RETIRED`, aprovação, atestação, último contato), sem dado pessoal além do `sub` (ADR-0030) | ED-18 |
| core `V12` | core | `platform.gis_db_role`: role de login do QGIS → tenant e usuário, estado, validade, gravada só pela rotina privilegiada de implantação (ADR-0034, decisão 7); número na ordem do merge | ED-55 (GIS-10) |
| `V1_5` | platform | `parameter_versions` (vigência com EXCLUDE, quem propõe ≠ quem aprova), `parameter_acceptances`, `regulatory_profiles`, `holidays` | P0.19 |
| `V1_6` | platform | `personal_data_access_log`; cota de busca por documento | P0.11 |
| `V1_7` | platform | `user_preferences` (modo e padrão de tema do usuário) | P1.18 |
| `V1_8` | platform | `org_units` (unidades organizacionais hierárquicas por tenant), áreas de cada unidade (localidade, setor, rota) e escopo do usuário por unidade e área (ADR-0040, v2.15) | P3.30 |
| `V3_4` | registry | GIN trigram sobre `unaccent(lower(name))` | P0.22 |
| `V3_5` | registry | sequência de matrícula por tenant | P1.4 |
| `V3_6` | registry | R2: `customer_types`, `property_profiles`, endereço, `block_faces`, rascunhos; `cadastral_change_requests` (alteração campo a campo ligada à OS) e `service_order_id` obrigatório nos históricos de cadastro (ADR-0039, v2.14) | P1.3 |
| `V3_7` | registry | `service_connections`: base da ligação de água ou de esgoto, 1:1 com o imóvel por tipo enquanto ativa, situação pelos catálogos existentes (antecipada da ED-13; ADR-0034) | P2.23 |
| `V9_1` | field | núcleo de OS: `service_types` (operação, efeito, interna ou de campo, aprovação, débito), `service_orders` refeita com FK e máquina de estados, `service_order_effects` (efeito aplicado uma vez), `service_order_batches`, vínculo com protocolo e unidade consumidora (ADR-0039; antecipada do P3, v2.14) | P1.31 |
| `V4_6` | billing | partição de `bills`/`bill_items`; auditoria por lote; CHECK de total com tributo | P2.3, P2.5 |
| `V4_7` | `sigsan-fiscal` | NFAg-e e NFS-e avulsa: leiaute, contingência, inutilização | P2.6, P3.6 |
| `V4_8` | billing | ajustes de conta: versão da conta retificada, operação, motivo (catálogo), anotação, aprovação, vínculo com o protocolo quando houver | P2.20 |
| `V15_1` | reporting | definições, execuções e agendamentos de relatório; views de relatório por domínio | P3.22 |
| `V5_3` / `V5_4` | metering | tarefas e importação de leitura / partição de `meter_readings`, chave de idempotência | P2.7 / P2.3 |
| `V6_3` | collection | partição de `payments`; `pix_inbox` | P3.2 |
| `V6_4` | collection | débito automático FEBRABAN 150 legado: autorizações migradas, remessas e retornos (só se houver optantes) | P3.24 |
| `V6_5` | collection | `refunds` ligada ao pagamento de origem (tipo, forma, situação, OS), fila de pagamentos não identificados e não classificados com a OS de identificação (v2.15) | P3.29 |
| `V8_2` | portal | vínculo da conta do consumidor (`sub` do realm `ziipsan-portal`) com a matrícula: método de comprovação, data, revogação; hash do código de vinculação por imóvel; tentativas de vinculação | P3.20 |
| `V13_1` | gis | classificação e catálogo: `gis_feature_class`, `gis_feature_type`, `gis_material`, `gis_catalog_item`, `gis_value_list`, `gis_connectivity_rule`, `gis_quality_target`, `gis_responsible_party`, `gis_domain` (v2.13) | P1.26 |
| `V13_2` | gis | feições (`gis_node`, `gis_arc`, `gis_connection`, `gis_structure`, `gis_containment`, `gis_zone`, `gis_poi`, `gis_attachment`), histórico `gis_*_history` com `EXCLUDE`, migração do `gis_network_element` para `gis_network_element_legacy` e view de compatibilidade | P1.26 |
| `R__gis_factory_catalog.sql` | gis | catálogo de fábrica (água e esgoto no núcleo; drenagem e resíduos na trilha), migration repetível por *upsert* de chave estável; fora da regra de versão crescente | P1.27 |
| `V13_3` | gis | topologia: gatilhos de snap e divisão, `gis_dirty_area`, `gis_validation_issue` (extensão `pgrouting` no init do banco, não aqui) | P2.21 |
| `V13_4` | gis | pacotes: `gis_change_set`, `gis_change_set_item`, `gis_change_set_review`, `gis_edit_lock`; funções da "rede na data" | P2.22 |
| `V13_5` | gis | vínculo do ramal com `service_connections`; proposta de ramais automáticos | P2.23 |
| `V13_6` | gis | publicação: `gis_layer`, `gis_style`, `gis_basemap`, `gis_wms_source`, `gis_tile_dirty` | P3.25 |
| `V13_7` | gis | importação: `gis_import_batch`, `gis_import_raw`, `gis_import_profile`, `gis_cad_layer_map`, `gis_quality_report` | P3.26 |
| `V13_8` | gis | `gis_thematic_map`; views do relatório de extensão | P3.27 |
| `V14_1`, `V14_2` | importação de legado | lotes, staging, conciliação; histórico de 120 meses | P1.2, P2.10 |
| `V14_3` | importação de legado | lotes de XML autorizados anteriores ao go-live, resultado da validação (assinatura, protocolo, emitente, duplicidade) e origem do documento | P2.19 |

**Faixas dos módulos novos (v2.4).** A trilha de editais começa depois da P3.12; suas migrations de tenant usam a
sequência global a partir de `V100`, com o módulo no nome. A faixa abaixo identifica o módulo (prefixo do nome do
arquivo, como `V1nn__accounting_chart_of_accounts.sql`, e catálogo de módulos) e só vale como número de versão se
alguma tabela precisar entrar antes da P3.12. `V4_8`, `V15_1`, `V6_5` e `V1_8` entram como faixa se a P2.20, a
P3.22, a P3.29 e a P3.30 forem concluídas antes da P3.12; senão, pela sequência global. O mesmo vale para `V13_6` a
`V13_8` (P3.25 a P3.27, v2.10);
`V13_1` a `V13_5` entram no P1 e no P2, antes da P3.12. Na trilha, as migrations do GIS seguem a sequência global com
`gis` no nome (`V1nn__gis_isolation_plan.sql`). Os metadados do Superset ficam em banco próprio, fora do
Flyway do produto; as roles e views de BI por tenant (ADR-0027) entram pela faixa `reporting`.

| Faixa | Módulo | Conteúdo | Entregas |
|---|---|---|---|
| `V13_x` | `gis` (amplia `V2_3`) | trilha do GIS: views e funções do QGIS, setores de manobra e planos de manobra, mapeamento hidráulico, drenagem, resíduos (rotas de coleta), pacotes offline de campo, OGC (inclusive as views de publicação lidas pelo GeoServer com a role do tenant, v2.12), ativos e códigos MCPSB; núcleo em `V13_1` a `V13_8` (reservas acima) | ED-53 a ED-56, ED-63 a ED-67 |
| `V15_x` | `reporting` (relatórios e BI) | definições e agendamentos de relatório, views por domínio, roles de BI por tenant e mapeamento role → tenant, catálogo de indicadores, metas e fechamentos | P3.22; ED-46 a ED-49 |
| `V16_x` | `accounting` (contabilidade) | plano de contas, rubricas, regras evento → lançamento, lançamentos, fechamento congelado, exportação diária | ED-32 (rubricas orçamentárias da ED-61 só em 2029) |
| `V17_x` | `active_debt` (dívida ativa) | notificação, inscrição, livro, CDA, parcelamento da dívida ativa, saldo mensal | ED-31 |
| `V18_x` | `omnichannel` | canais (WhatsApp, Messenger, Instagram, Telegram, e-mail, chat do portal), conversas, mensagens, filas, transbordo, sessões do bot, consentimentos por canal, senhas e painel de chamada | ED-40 a ED-43 |
| `V19_x` | `quality` (qualidade e laboratório; amplia `V2_1`, `V2_9`) | catálogo normativo versionado, plano de amostragem, cadeia de custódia, boletins, conformidade, laboratório e calibração (17025), exportação SISAGUA | ED-50, ED-51 |
| `V20_x` | `erp` (`inventory`, `fleet`) | almoxarifado e material da OS (`approval_rule`, `material_requisition`, `requisition_item`, `stock_reservation`, `stock_movement`, `approval_decision`, ADR-0032); veículos, abastecimento, manutenção e equipes de campo. `purchasing`, `hr` e `budget` só em 2029, sob demanda (ADR-0028, item i, ajuste v2.5) | ED-58, ED-59 |
| `V21_x` | `certification` (certificações de qualidade) | ativação por norma com escopo e edição; mapa de cláusulas e vínculo de evidência; documentos, riscos, objetivos, mudanças, treinamentos, fornecedores, requisitos legais, auditorias e achados, ocorrências e ações, análises críticas; pacotes por norma | P1.32, P2.25, P3.31; ED-69 a ED-82 |

`current_tenant_id()` como SQL STABLE (17:M10) não é migration: a função é de `sigsan_owner` e nasce no init de
segurança (`infra/postgres/init/sql/security.sql`); o migrador não pode redefini-la, por desenho. Nos bancos existentes,
script de operação como `postgres`.

---


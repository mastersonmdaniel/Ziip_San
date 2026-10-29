## 3. Mapa de versões Flyway

Duas histórias: **core** (`public`, uma vez por banco) e **tenant** (uma por schema). Existentes, conferidas em
`**/db/migration/**` em 12/10/2026 e de novo pelo 23c em 28/10/2026: core `V1`, `V2`; tenant `V1_1`–`V1_4`,
`V2_1`–`V2_10`, `V3_1`–`V3_3`, `V4_1`, `V5_1`, `V5_2`, `V6_1`. Este mapa é a referência das versões citadas nas
tarefas; cada versão aparece uma só vez.

**Regra de ordem.**
- Core nunca usa `outOfOrder`: o número definitivo sai na ordem do merge; a tabela mostra o número esperado e é
  corrigida se a ordem mudar. Entre fases, a ordem é garantida pela própria sequência das fases.
- Subversões do core: tabelas novas do core entram como subversão da reserva da mesma fase (core `V5_1`, `V9_1`…), que
  o Flyway ordena entre as inteiras. Quando a fase seguinte já tem subversões reservadas, a tabela nova usa um terceiro
  nível (core `V10_2_1`, entre `V10_2` e `V10_3`). O prefixo "core" distingue essas versões das de tenant de mesmo
  número, que ficam em outra tabela de histórico.
- Tenant: faixa por módulo com `outOfOrder(true)` até a P3.12; até lá todo tenant é recriável (massa sintética ou
  reimportação do legado, que segue como fonte da verdade). Na P3.12, antes do paralelo, `outOfOrder(false)`; daí em
  diante a versão vem de uma sequência global a partir de `V100`, com o módulo no nome (`V100__dunning_actions.sql`), e o
  CI recusa versão menor ou igual à maior publicada.
- A P3.12 fecha depois das tarefas de P0 a P3 que usam versão de faixa e estão antes dela na ordem da fase (na coluna
  Dep., ao menos P3.22, P3.29, P3.30 e P3.31). Tarefa de P3 que use faixa e ainda não esteja concluída quando a P3.12
  fechar passa à sequência global, com o módulo no nome; a reserva abaixo continua identificando o conteúdo.
- Migrations repetíveis (`R__`) carregam catálogo de fábrica por *upsert* de chave estável; ficam fora da regra de
  versão crescente e nunca apagam linha usada por registro do tenant.
- Expand/contract: coluna só sai na release seguinte à que deixou de usá-la.
- A coluna "Módulo" usa o nome curto do módulo; o nome Maven definitivo (`platform/*` ou `packages/sanitation/*`) é o da
  tabela única de módulos de PLATAFORMA-E-VERTICAIS, fixada na P0.47 (COE-16). Os schemas `platform` e `support` do
  banco do core (ADR-0018, ADR-0020) entram pelas versões do core; só o banco principal, o do catálogo, os usa, e nos
  clusters adicionais (ADR-0005) ficam vazios, salvo decisão diferente na P1.14.
- Renomeação (P0.33): muda o checksum das migrations existentes, porque os `GRANT` citam as roles; aceito porque não há
  banco com dado real, e banco e Keycloak de desenvolvimento são recriados. A `V1_1` é reescrita antes, na limpeza da
  P0.25, sem `users`, `roles`, `user_roles`, `tenant_metadata`, `documents` e `service_orders` (COD-09). Depois da P0.33,
  as migrations voltam a ser imutáveis.

**Core**

| Versão | Conteúdo | Tarefa | Situação |
|---|---|---|---|
| core `V1`, `V2` | catálogo de tenants, `audit_log` e `tenant_settings` com RLS forçada; redação | — | existentes |
| core `V3` | `parameter_definitions`, `parameter_legal_limits` (catálogo global do produto, só leitura para a aplicação) | P0.19 | reservada, P0 |
| core `V4` | `tenant_catalog.cluster_id`, `isolation_mode`; revisão do `schema_name UNIQUE` (`core/V1:21`); `tenant_schema_version`; `ziipsan_app` só com SELECT no catálogo | P0.17, P0.21, P1.14 | reservada, P0 |
| core `V5` | sessões do BFF | P0.14 | reservada, P0 |
| core `V5_1` | schema `platform`; `platform.deployment` (registro de implantação), gravada pelo `migrate` (ADR-0021) | P0.42 | reservada, P0 |
| core `V6` | `tenant_key` (DEK por tenant) | P0.28 | reservada, P0 |
| core `V6_1` | `platform.vertical` (id, versão, estado) e `platform.tenant_vertical` (tenant, vertical, ativação, estado); todo tenant existente nasce com `sanitation` ativo. Antes "core `V9_7`": a P0.47 é do P0 e não pode vir depois das versões do P1 | P0.47 | reservada, P0 |
| core `V7` | `audit_log` particionado por mês, PK `(created_at, id)` com UUIDv7, retenção | P1.7 | reservada, P1 |
| core `V8` | `outbox` com `tenant_id`, RLS e partição diária (ADR-0014: uma outbox por banco; substitui a faixa `V11_x` de tenant da v1) | P1.9 | reservada, P1 |
| core `V9` | console v0 no schema `platform`: `plan`, `tenant_subscription`, `feature_flag`, `onboarding_step`, `tenant_lifecycle`, `audit_event`; sem impersonação (o acesso assistido vai para o core `V10_3`) | P1.6 | reservada, P1 |
| core `V9_1` | Central v0: `platform.metric_definition`, `metric_point` (partição diária), `metric_daily`, `alert_rule`, `alert_event`, `config_item`; role `ziipsan_central`; funções `platform_stats.*` | P1.17 | reservada, P1 |
| core `V9_2` | `public.tenant_assets` (logo do tenant, com estado da varredura); função `SECURITY DEFINER` da marca pré-login, restrita às chaves `branding.*` de `tenant_settings` | P1.19 | reservada, P1 |
| core `V9_3` | schema `support`; conteúdo de ajuda: `article`, `article_version`, `article_feedback`, `tour_completion`, `error_explanation` | P1.21 | reservada, P1 |
| core `V9_4` | `support.ticket`, `ticket_message`, `ticket_attachment`, `ticket_event`; sequência do chamado `SUP-AAAA-NNNNNN` | P1.23 | reservada, P1 |
| core `V9_5` | `platform.document_acceptance`; `support.release_note_view` | P1.24 | reservada, P1 |
| core `V9_6` | definições dos parâmetros `gis.*` em `parameter_definitions` e limites em `parameter_legal_limits` (SRID de trabalho, datum vertical, tolerâncias de snap, distância do ramal automático, metas de precisão, mapa base, perfil de simbologia, piso de agregação) (ADR-0034) | P1.26 | reservada, P1 |
| core `V9_7` | vago (era da P0.47, que passou ao core `V6_1`); não é reaproveitado | — | — |
| core `V9_8` | catálogo global de normas: `platform.cert_standard`, `cert_standard_edition` (com vigência futura) e `cert_clause` (número de cláusula e mapa em palavras próprias, sem texto da norma) | P1.32 | reservada, P1 |
| core `R__norm_catalog.sql` | carga do catálogo `docs/normas/catalogo-normas.json` nas tabelas do core `V9_8`: metadados, resumos em palavras próprias, classe, situação, selos de verificação e de vigência; nunca texto de norma | P1.34 | repetível, P1 |
| core `V10` | `job_runs` e travas por (tenant, ciclo) | P2.1 | reservada, P2 |
| core `V10_1` | contrato SaaS no schema `platform`: contrato (com a política de SLA), aditivo, empenho, reajuste, fatura, pagamento, referências aos documentos da fatura e da NFS-e | P2.12–P2.14 | reservada, P2 |
| core `V10_2` | `support.sla_policy` (cinco severidades do nível Setor Público, ADR-0025: prazo de solução, aderência e multa; matriz impacto × criticidade), `csat_response`; prazos e pausa no chamado | P2.15 | reservada, P2 |
| core `V10_2_1` | `platform.tenant_domain`: domínio próprio do tenant (CNAME), verificação de posse, estado do certificado, renovação e revogação; resolução do tenant pelo host antes do login | P2.27 | reservada, P2 |
| core `V10_3` | `support.access_consent`, `assisted_access_session` (tipo `CONSENTED` ou `EMERGENCY`; na emergência, referência ao incidente no lugar do consentimento) | P3.17 | reservada, P3 |
| core `V10_4` | `support.status_component`, `status_incident`, `status_update`, `maintenance_window` | P3.18 | reservada, P3 |
| core `V10_5` | período de consulta somente leitura pós-contrato no ciclo de vida do tenant (início, fim, usuários), antes da carência (ADR-0025) | P3.16 | reservada, P3 |
| core `V10_6` | `support.vulnerability_report` (recebimento, triagem, resposta e divulgação coordenada) e `platform.subprocessor` (lista pública de subprocessadores da página de confiança) | P3.38 | reservada, P3 |
| core `V11` | `platform.app_device`: dispositivos do app de campo por tenant (estado `ACTIVE`, `BLOCKED` ou `RETIRED`, aprovação, atestação, último contato), sem dado pessoal além do `sub` (ADR-0030). Antes "core `V11_1`": a ED-18 é da onda A e vem antes da ED-39 | ED-18 | reservada, onda A |
| core `V11_1` | configuração do app do cliente por tenant (identificador do app, contas de loja, versão publicada, ADR-0026). Antes "core `V11`", junto da instância dedicada, que passou ao core `V13` | ED-39 | reservada, onda B |
| core `V12` | `platform.gis_db_role`: role de login do QGIS → tenant e usuário, estado, validade, gravada só pela rotina privilegiada de implantação (ADR-0034, decisão 7) | ED-55 (GIS-10) | reservada, onda B |
| core `V13` | instância dedicada no catálogo: modo de isolamento, ambiente e conta da instância (ADR-0024) | LC.15 | reservada, LC |

**Tenant, por módulo**

| Versão | Módulo | Conteúdo | Tarefa | Situação |
|---|---|---|---|---|
| `V1_1`–`V1_4` | platform | schema do tenant, helper de tabela, auditoria endurecida, redação; `V1_1` reescrita na P0.25 sem as tabelas legadas | P0.25 | existentes |
| `V1_5` | platform | `parameter_versions` (vigência com EXCLUDE, quem propõe ≠ quem aprova), `parameter_acceptances`, `regulatory_profiles`, `holidays` (sai do `V6_1`) | P0.19 | reservada, P0 |
| `V1_6` | platform | `personal_data_access_log`; cota de busca por documento | P0.11 | reservada, P0 |
| `V1_7` | platform | `user_preferences` (modo e padrão de tema do usuário) | P1.18 | reservada, P1 |
| `V1_8` | platform | `org_units` (unidades organizacionais hierárquicas por tenant), áreas de cada unidade (localidade, setor, rota) e escopo do usuário por unidade e área (ADR-0040) | P3.30 | reservada, P3 |
| `V1_9` | platform | `users` recriada sem `password_hash` nem `mfa_secret` (a credencial fica só no Keycloak), com `UNIQUE (tenant_id, id)` para FK composta de leiturista, executor e aprovador (DAD-13), `status` por domínio (DAD-31) e atribuição de perfis do catálogo `recurso:ação` ao usuário, com vigência | P0.13 | reservada, P0 |
| `V1_10` | platform | `saved_views`: visões salvas por usuário (filtros, colunas, ordenação, densidade) | P1.33 | reservada, P1 |
| `V1_11` | platform | `work_items` (aprovações e pendências de OS, retificação, parâmetro e documento, com origem, responsável e vencimento) e `work_item_delegations` (início, fim, registro) | P2.28 | reservada, P2 |
| `V1_12` | platform | `document_templates`, `template_versions` (variáveis tipadas, vigência, aprovação por outra pessoa, marca do tenant) | P2.30 | reservada, P2 |
| `V1_13` | platform | colunas de origem em `regulatory_profiles` e nos valores do perfil (agência, ato, artigo, vigência), simulação antes da ativação e troca na data de vigência do ato | P2.31 | reservada, P2 |
| `R__regulatory_profile_catalog.sql` | platform | perfis de fábrica das agências de SC (ARIS, ARESC, AGIR, AGR Tubarão, CISAM Meio-Oeste, CISAM Sul), com chave, valor, artigo de origem e vigência | P2.31 | repetível, P2 |
| `V1_14` | platform | assinaturas eletrônicas por documento (nível da Lei 14.063/2020, signatário, carimbo de tempo, verificação) | P3.34 | reservada, P3 |
| `V1_15` | platform | `notifications` (central de avisos por usuário, leitura registrada) e `notification_preferences` (canal e finalidade; aviso legal não desligável) | P3.36 | reservada, P3 |
| — | platform | página inicial por perfil e agenda (P3.39): vem depois da P3.12, pela sequência global com `platform` no nome | P3.39 | sequência global |
| `V2_1`–`V2_10` | quality, losses, gis, mtr, lgpd | schemas; redação; cifra do MTR em `V2_8` | — | existentes |
| `V3_1`–`V3_3` | registry | schema; redação | — | existentes |
| `V3_4` | registry | GIN trigram sobre `unaccent(lower(name))` | P0.22 | reservada, P0 |
| `V3_5` | registry | sequência de matrícula por tenant | P1.4 | reservada, P1 |
| `V3_6` | registry | R2: `customer_types`, `property_profiles`, endereço, `block_faces`, rascunhos; dicionário-alvo do 23b; `cadastral_change_requests` (alteração campo a campo ligada à OS) e `service_order_id` obrigatório nos históricos de cadastro (ADR-0039) | P1.3 | reservada, P1 |
| `V3_7` | registry | `service_connections`: base da ligação de água ou de esgoto, 1:1 com o imóvel físico (lote) por tipo enquanto ativa, com várias unidades consumidoras por ligação em condomínio (§8, glossário); situação pelos catálogos existentes (ADR-0034) | P2.23 | reservada, P2 |
| `V3_8` | registry | situações do titular: representante legal e terceiro autorizado (escopo e validade), sucessão por falecimento, nome social, unidade desocupada, fusão e separação de cadastros (com OS inversa), resultado da validação externa de CPF e CNPJ e alerta de óbito com revisão humana | P1.41 | reservada, P1 |
| — | registry | regras e fila de qualidade cadastral (P3.32): vem depois da P3.12, pela sequência global com `registry` no nome | P3.32 | sequência global |
| `V4_1` | billing | schema | — | existente |
| `V4_2` | billing | tarifa social | P1.5 | reservada, P1 |
| `V4_3`, `V4_4` | billing | ciclo; modelos de conta | P2.5 | reservadas, P2 |
| `V4_5` | fiscal | tributos com vigência (CBS e IBS) e motor fiscal: certificados, séries, documentos, eventos, lotes | P1.8 | reservada, P1 |
| `V4_6` | billing | partição de `bills`/`bill_items`; auditoria por lote; CHECK de total com tributo | P1.16, P2.3, P2.5 | reservada, P1–P2 |
| `V4_7` | fiscal | NFAg-e e NFS-e avulsa: leiaute, contingência, inutilização | P2.6, P3.6 | reservada, P2–P3 |
| `V4_8` | billing | ajustes de conta: versão da conta retificada, operação, motivo (catálogo), anotação, aprovação, `protocol_id`, `service_order_id`, `bill_status_events` | P2.20 | reservada, P2 |
| `V4_9` | billing | versões da estrutura tarifária (reajuste por índice, revisão por ato da agência, cópia de versão) e simulações de impacto, que não alteram a base | P2.29 | reservada, P2 |
| `V4_10` | billing | conteúdo mínimo da conta por lei e por agência como dado versionado e mensagens legais emitidas por conta, com prova de emissão | P2.32 | reservada, P2 |
| `R__bill_legal_requirements.sql` | billing | carga de fábrica dos campos obrigatórios da conta (Lei 11.445, art. 39; CDC, art. 42-A; Lei 12.039; Decreto 5.440) e dos requisitos das agências de SC | P2.32 | repetível, P2 |
| `V4_11` | fiscal | pendências e ações do operador sobre documento fiscal do tenant (rejeição, contingência a transmitir, inutilização, cancelamento pendente, reenvio), com histórico | P2.33 | reservada, P2 |
| `V4_12` | billing | cashback de CBS e IBS: elegibilidade por domicílio e período de aferição, devolução por domicílio e reflexo na conta | P2.34 | reservada, P2 |
| `V5_1`, `V5_2` | metering | schema; redação | — | existentes |
| `V5_3` | metering | tarefas e importação de leitura | P2.7 | reservada, P2 |
| `V5_4` | metering | partição de `meter_readings`, chave de idempotência | P2.3 | reservada, P2 |
| `V6_1` | collection | schema | — | existente |
| `V6_2` | collection | conciliação, Pix | P3.1 | reservada, P3 |
| `V6_3` | collection | partição de `payments`; `pix_inbox` | P3.2 | reservada, P3 |
| `V6_4` | collection | débito automático FEBRABAN 150 legado: autorizações migradas, remessas e retornos (só se houver optantes) | P3.24 | reservada, P3 |
| `V6_5` | collection | `refunds` ligada ao pagamento de origem (tipo, forma, situação, OS); fila de pagamentos não identificados e não classificados com a OS de identificação | P3.29 | reservada, P3 |
| `V7_1` | integration | clientes da API pública por tenant (escopos, cota, ambiente de teste), assinaturas de webhook e entregas com reenvio | P2.26 | reservada, P2 |
| — | integration | conectores, saúde, mensagens com falha e reprocessamento do hub de integrações (P3.37): vem depois da P3.12, pela sequência global com `integration` no nome | P3.37 | sequência global |
| `V8_1` | portal (atendimento) | protocolo de atendimento: número, canal, solicitante, unidade consumidora ou local, motivo classificado, prazo, vínculo 1:N com OS (FK do `protocol_id` da OS). Antes da P3.4 | P1.36 | reservada, P1 |
| `V8_2` | portal | vínculo da conta do consumidor (`sub` do realm `ziipsan-portal`) com a matrícula: método de comprovação, data, revogação; hash do código de vinculação por imóvel; tentativas de vinculação | P3.20 | reservada, P3 |
| `V8_3` | portal | código público de verificação da fatura, consultas registradas sem dado do titular, canais oficiais do tenant | P3.35 | reservada, P3 |
| `V9_1` | OS (núcleo, ADR-0045) | motor de OS: `service_types` (operação, efeito, interna ou de campo, aprovação, referência ao item de preço), `service_orders` com FK e máquina de estados, `service_order_effects` (efeito aplicado uma vez), `service_order_batches`, alvos tipados, vínculo com protocolo e unidade consumidora (ADR-0039) | P1.31 | reservada, P1 |
| `V9_2` | field | bloqueios de corte; seleção mínima do aviso, prova de entrega e motivos de não geração | P3.4 | reservada, P3 |
| `V13_1` | gis | classificação e catálogo: `gis_feature_class`, `gis_feature_type`, `gis_material`, `gis_catalog_item`, `gis_value_list`, `gis_connectivity_rule`, `gis_quality_target`, `gis_responsible_party`, `gis_domain` | P1.26 | reservada, P1 |
| `V13_2` | gis | feições (`gis_node`, `gis_arc`, `gis_connection`, `gis_structure`, `gis_containment`, `gis_zone`, `gis_poi`, `gis_attachment`), histórico `gis_*_history` com `EXCLUDE`, migração do `gis_network_element` para `gis_network_element_legacy` e view de compatibilidade; `UNIQUE (tenant_id, code)` em `dma_district` e `leak_detection` | P1.26 | reservada, P1 |
| `R__gis_factory_catalog.sql` | gis | catálogo de fábrica (água e esgoto no núcleo; drenagem e resíduos na trilha) | P1.27 | repetível, P1 |
| `V13_3` | gis | topologia: gatilhos de snap e divisão, `gis_dirty_area`, `gis_validation_issue` (extensão `pgrouting` no init do banco, não aqui) | P2.21 | reservada, P2 |
| `V13_4` | gis | pacotes de edição: `gis_change_set`, `gis_change_set_item`, `gis_change_set_review`, `gis_edit_lock`; funções da "rede na data" | P2.22 | reservada, P2 |
| `V13_5` | gis | vínculo do ramal com `service_connections`; proposta de ramais automáticos | P2.23 | reservada, P2 |
| `V13_6` | gis | publicação: `gis_layer`, `gis_style`, `gis_basemap`, `gis_wms_source`, `gis_tile_dirty` | P3.25 | reservada, P3 |
| `V13_7` | gis | importação: `gis_import_batch`, `gis_import_raw`, `gis_import_profile`, `gis_cad_layer_map`, `gis_quality_report` | P3.26 | reservada, P3 |
| `V13_8` | gis | `gis_thematic_map`; views do relatório de extensão | P3.27 | reservada, P3 |
| `V14_1`, `V14_2` | importação de legado | lotes, staging, conciliação; histórico de 120 meses | P1.2, P2.10 | reservadas, P1–P2 |
| `V14_3` | importação de legado | lotes de XML autorizados anteriores à primeira produção, resultado da validação (assinatura, protocolo, emitente, duplicidade) e origem do documento | P2.19 | reservada, P2 |
| `V14_4` | importação | importador genérico de planilha: modelos, área de preparação, validação, relatório de recusas, vínculo com a OS de lote e histórico | P1.40 | reservada, P1 |
| `V15_1` | reporting | definições, execuções e agendamentos de relatório; views de relatório por domínio | P3.22 | reservada, P3 |
| `V15_2` | reporting | registro único de indicadores: definição, fórmula, fonte, meta, periodicidade, grau de confiança, fechamento e semáforo | P3.41 | reservada, P3 |
| `V21_1` | certification | núcleo do módulo Certificações de qualidade: ativação por norma com escopo, edição e aceite; mapa de cláusulas e vínculo de evidência | P1.32 | reservada, P1 |
| `V21_2` | certification | informação documentada; ocorrência e ação | P2.25 | reservada, P2 |
| `V21_3` | certification | riscos, objetivos, auditoria interna, análise crítica, competência, fornecedor e requisitos legais | P3.31 | reservada, P3 |
| `V23_1` | lgpd (amplia `V2_5`, `V2_7`) | registro de operações de tratamento por finalidade e base legal, pedidos do titular ligados ao protocolo de atendimento, encarregado de dados do tenant | P3.40 | reservada, P3 |
| `V27_1` | asset | `asset` com hierarquia de seis níveis (município, sistema, etapa do serviço, instalação, equipamento, componente), classe e tipo do catálogo da P1.27, códigos MCPSB, indicadores de bem compartilhado e reversível | P1.35 | reservada, P1 |

**Tarefas novas sem migration.** P1.37 (perfis de fábrica declarados no manifesto do pacote e compostos do catálogo de
permissões versionado; a atribuição ao usuário usa a `V1_9`); P1.38 (catálogo de eventos declarado no `vertical.json` e
lido na subida); P1.39 (o CNPJ alfanumérico já é gravado cifrado, com índice cego, em colunas de texto; coluna de
formato restrito encontrada na conferência entra na próxima versão livre do módulo dono); P3.33 (restauração e desfazer
operam sobre as tabelas existentes, com registro na auditoria); LC.15 cria ambiente, não tabela de tenant (o registro
no catálogo é o core `V13`). ED-104 e ED-105 são da trilha e usam a sequência global (abaixo).

**Faixas dos módulos (trilha de editais).** A trilha começa depois da P3.12; suas migrations de tenant usam a sequência
global a partir de `V100`, com o módulo no nome (`V1nn__accounting_chart_of_accounts.sql`). A faixa identifica o módulo
(prefixo do nome do arquivo e catálogo de módulos) e só vale como número de versão se alguma tabela precisar entrar
antes da P3.12. Os metadados do Superset ficam em banco próprio, fora do Flyway do produto; as roles e views de BI por
tenant (ADR-0027) entram pela faixa `reporting`.

| Faixa | Módulo | Conteúdo | Entregas |
|---|---|---|---|
| `V7_x` | `integration` | SINISA, e-Sfinge e conectores de ERP; API pública e hub de integrações nas fases (`V7_1`, P3.37) | ED-33, ED-62 |
| `V8_x` | `portal` (atendimento) | atendimento completo, ouvidoria e visão 360 sobre o protocolo da `V8_1` | ED-35, ED-38 |
| `V10_x` | `dunning` | cobrança, negativação e protesto; cobrança por empresa terceirizada (pacote pendente) | P4.1, ED-30; ED-68 |
| `V12_x` | `grs` (amplia `V2_4`, `V2_6`, `V2_8`) | MTR, CDF e DMR, lista IBAMA, NBR 10004 série 2024, lodo como gerador, operação de resíduos sólidos | ED-104 (pacote pendente) |
| `V13_x` | `gis` (amplia `V2_3`) | trilha do GIS: views e funções do QGIS, setores e planos de manobra, mapeamento hidráulico, drenagem, resíduos (rotas de coleta), pacotes offline de campo, OGC (inclusive as views lidas pelo GeoServer com a role do tenant) | ED-53 a ED-55, ED-63 a ED-67 |
| `V15_x` | `reporting` | relatórios e BI: views por domínio, roles de BI por tenant e mapeamento role → tenant; catálogo SINISA e painéis sobre o registro de indicadores da `V15_2` | ED-46 a ED-49 |
| `V16_x` | `accounting` | plano de contas, rubricas, regras evento → lançamento, lançamentos, fechamento congelado, exportação diária | ED-32 (rubricas orçamentárias da ED-61 só em 2029) |
| `V17_x` | `active_debt` | notificação, inscrição, livro, CDA, parcelamento da dívida ativa, saldo mensal | ED-31 |
| `V18_x` | `omnichannel` | canais (WhatsApp, Messenger, Instagram, Telegram, e-mail, chat do portal), conversas, mensagens, filas, transbordo, sessões do bot, consentimentos por canal, senhas e painel de chamada | ED-40 a ED-43 |
| `V19_x` | `quality` (amplia `V2_1`, `V2_9`) | catálogo normativo versionado, plano de amostragem, cadeia de custódia, boletins, conformidade, laboratório e calibração (17025), exportação SISAGUA; catálogo de padrão de efluente com vigência futura, automonitoramento e eficiência de DBO; lodo de ETA e ETE; esgoto não doméstico; água bruta | ED-50, ED-51, ED-105, ED-83, ED-80 |
| `V20_x` | `erp` (`inventory`, `fleet`) | almoxarifado e material da OS (`approval_rule`, `material_requisition`, `requisition_item`, `stock_reservation`, `stock_movement`, `approval_decision`, ADR-0032); veículos, abastecimento, manutenção e equipes de campo; `purchasing`, `hr` e `budget` só em 2029, sob demanda | ED-58, ED-59, ED-89 |
| `V21_x` | `certification` | pacotes por norma sobre o núcleo das `V21_1` a `V21_3`; evidência e auditor externo | ED-69 a ED-77, ED-80 a ED-82 |
| `V22_x` | `losses` (amplia `V2_2`, `V2_10`) | setorização por DMC, macromedição, vazão mínima noturna, balanço hídrico por DMC e da NR 15, gestão de pressão, anomalia em séries, perdas aparentes | ED-78, ED-85, ED-86, ED-94, ED-95 |
| `V23_x` | `lgpd` (amplia `V2_5`, `V2_7`) | o que a ISO/IEC 27701 pede além da lei, sobre a `V23_1` | ED-74 |
| `V24_x` | `station_operation` | diário, ronda e turno de ETA, ETE, elevatória e reservatório; insumos químicos; energia; sala de situação | ED-87, ED-89, ED-90, ED-93 |
| `V25_x` | `network_incident` | ocorrência de rede com área afetada, início e fim, causa e OS; crise hídrica, rodízio e racionamento | ED-92, ED-91 |
| `V26_x` | `regulatory` | instrumento contratual do prestador, pacote regulatório de saída por agência, agenda de obrigações, transparência ativa e dados abertos | ED-96 a ED-99 |
| `V27_x` | `asset` | inventário regulatório, conciliação com o patrimônio, manutenção e risco sobre a `V27_1`; ISO 55001 | ED-56, ED-72 |

`current_tenant_id()` como SQL STABLE (17:M10) não é migration: a função é de `ziipsan_owner` e nasce no init de
segurança (`infra/postgres/init/sql/security.sql`); o migrador não pode redefini-la, por desenho. Nos bancos existentes,
script de operação como `postgres`.

---

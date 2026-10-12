# Central Ziip do Ziip SAN

Decisões: ADR-0018 (Central própria), ADR-0019 (cobrança por contrato), ADR-0020 (suporte e acesso assistido) e
ADR-0022 (área do tenant). Base: revisão 17 (achado P1), ADR-0005, ADR-0007, ADR-0008, ADR-0014, ADR-0015,
`PARAMETROS-DO-TENANT.md` §5 e `REQUISITOS-NAO-FUNCIONAIS.md`. Referência: Ziip Central do Ziip Cloud
(`Nfse.Api/Servicos/CentralEndpoints.cs`, `CentralColetorService.cs`, `Nfse.Domain/Central.cs`). Documento de
12/10/2026, revisto no mesmo dia com as decisões do dono do produto sobre suspensão, eliminação e acesso assistido e,
depois, com as do plano v2.2 (§2.4): carência de 30 dias, papéis que suspendem e eliminam, acesso de emergência e
identificadores em inglês; nada foi implementado. Nomes de role e realm já seguem a renomeação `ziipsan` (plano P0.33).

## 1. Objetivos

1. Mostrar, por tenant e no conjunto, se o saneamento de cada cliente está rodando: ciclo, arrecadação, fiscal, prazos.
2. Operar a plataforma: implantar, configurar, suspender e encerrar tenants, com prova de cada ação.
3. Alertar antes que o problema chegue ao consumidor do tenant ou ao regulador.
4. Fazer tudo isso sem ler dado pessoal de tenant. As únicas leituras de dado do tenant pela plataforma são o acesso
   assistido, com consentimento, e o acesso de emergência em incidente de segurança declarado (§7), ambos pela API do
   tenant com a identidade do operador, nunca pela role da Central.
5. Expor métricas em formato estável, para uma futura Central única dos produtos Ziip (fora do escopo).

## 2. Quem acessa

Papéis de plataforma no realm `ziipsan`, sem `tenant_id`. O token de plataforma não abre rota de tenant, salvo pelo
acesso assistido ou de emergência (§7). Toda rota `/api/v1/platform/*` exige segundo fator verificado no token (`amr`), sem exceção
(ADR-0007); sem ele, 403 `mfa_obrigatorio`.

| Papel | Pode | Exige também |
|---|---|---|
| `PLATFORM_OWNER` (dono) | tudo; suspensão e bloqueio por qualquer motivo; eliminação de dados de tenant (exclusiva); acesso de emergência (§7) | step-up e justificativa nas ações destrutivas; incidente declarado na emergência |
| `PLATFORM_OPS` (operação) | painéis técnicos, alertas, implantação, migrações, flags de rollout | — |
| `PLATFORM_SUPPORT` (suporte) | painéis do tenant, fila de chamados, pedido de acesso assistido | consentimento do tenant |
| `PLATFORM_FINANCE` (financeiro) | contratos, faturas, pagamentos, cotas (ADR-0019); suspensão e bloqueio só por inadimplência contratual, e a reativação correspondente | step-up e justificativa; fatura vencida e aviso prévio registrados |

Suspensão, bloqueio e eliminação de dados de tenant são executados por uma única pessoa com MFA, sem segundo
aprovador, sempre com step-up, justificativa e auditoria (decisões do dono do produto, 12/10/2026). Suspensão e
bloqueio: `PLATFORM_OWNER` por qualquer motivo, ou `PLATFORM_FINANCE` só com o motivo inadimplência contratual, que
exige referência a fatura vencida e ao aviso prévio registrados no contrato (ADR-0019); com outro motivo, o pedido do
financeiro é recusado com 403. Eliminação: só `PLATFORM_OWNER`, depois da carência de 30 dias e com certificado de
descarte (§6.3). A aprovação dupla continua onde o ADR-0015 e `PARAMETROS-DO-TENANT.md` a exigem (parâmetros do
tenant, ajuste de conciliação) e na liberação da emissão fiscal em produção (`FISCAL-PORTE-JAVA.md` §3.6). Toda ação
mutante grava em `platform.audit_event` (§4).

## 3. Abas e indicadores

Frequência padrão: coleta a cada 5 min. "Tenant" significa indicador por tenant e consolidado.

| Aba | Indicador | Fonte | Freq. | Alerta |
|---|---|---|---|---|
| Visão geral | Health Score 0–100 por tenant (fatores abaixo); "3 coisas para hoje"; tendências de 7 dias; briefing diário | métricas da Central | 5 min; briefing às 8h | score < 65 |
| Faturamento | ciclos abertos, % de rotas concluídas, atraso sobre o calendário do tenant, rotas com falha | `job_runs` | 5 min | rota falha; ciclo além da janela; RNF-05 |
| Arrecadação | retornos recebidos e pendentes por convênio; divergências de conciliação abertas | tabelas de retorno (P3.1) | 15 min | retorno esperado ausente no dia útil; divergência > 2 dias |
| Pix | idade da última notificação por PSP; p99 do webhook; `pix_inbox` pendente | `pix_inbox`, Micrometer | 5 min | RNF-06; PSP sem notificação em horário comercial por 1 h |
| Fiscal | autorizadas, rejeições por código, idade da fila, certificado a vencer | outbox `fiscal`, documentos fiscais | 5 min | RNF-08; rejeição > 2% no dia; certificado ≤ 30 dias |
| Regulatório | parâmetros com `valid_to` em 60 dias; feriados do ano seguinte ausentes; tarifa social sem conferência no DOU; janela do SINISA assistido | `parameter_versions`, catálogo de limites | diária | prazo ≤ 30 dias; parâmetro bloqueante ausente |
| Plataforma | migração por tenant e onda (ok, pendente, quarentena); tenants e ligações por shard contra o teto; profundidade e idade por fila; mensagens `DEAD` | `tenant_schema_version`, catálogo, outbox | 5 min | quarentena; 80% do teto (RNF-21); RNF-09; `DEAD` > 0 |
| Infraestrutura | SLO de 30 dias e burn rate (RNF-01, 02, 10, 11); idade do WAL arquivado; última restauração testada; TLS; uptime externo | backend OTel, ferramenta de backup, sonda externa | 5 min | RNF-53; WAL > 15 min (RNF-14); restauração > 90 dias |
| Segurança | % de MFA por papel e tenants com MFA afrouxado; falhas de login (z-score de 14 dias); buscas por documento com 429; tentativas de vinculação no portal com 429; sessões de acesso assistido e de emergência ativas; integridade da trilha | Keycloak, contadores, `audit_log` | 5 min; trilha a cada 6 h | pico de login; cota excedida (ADR-0008); trilha quebrada |
| LGPD | pedidos de titular por status e prazo (só contagem); expurgos executados; tenants em saída e prazos; kit de evidências | módulo LGPD, `platform.tenant_lifecycle` | diária | pedido a 3 dias do prazo; RNF-42 em risco |
| Suporte | definido no ADR-0020 | — | — | — |
| Contratos | vigência, saldo de empenho, cota usada, faturas em aberto, pagamento × fatura | ADR-0019 | diária | ADR-0019, item 7 |
| Radar | normas e NTs acompanhadas, com situação e data | `POST` de agentes (skill `monitor-regulatorio`) | sob demanda | norma com prazo ≤ 30 dias |
| Alertas e Config. | ativos, histórico, MTTR de 90 dias; regras, soneca, canais (e-mail, push, webhook) | `platform.alert_*` | ao vivo | — |

**Health Score.** Disponibilidade e erros 5xx do tenant (25), ciclo e lote (25), fiscal (20), arrecadação e Pix (15),
conformidade: parâmetros, MFA e prazos (15). Cada fator mostra os pontos e o motivo. Saudável ≥ 85, atenção ≥ 65,
crítico abaixo. Fator sem fonte ainda (antes da fase que o cria) entra com peso redistribuído e aviso "não medido".

Modo TV: Visão geral com atualização a cada 60 s, sem nome de pessoa na tela.

## 4. Modelo de dados

Schema `platform` no banco do core, criado pelo `migrate` (versões no plano §3: core `V5_1`, `V9`, `V9_1`, `V9_5`,
`V10_1`). Nenhuma tabela guarda dado pessoal de tenant: só `tenant_id` (UUID), contagens, durações e códigos. Dados de
operadores da plataforma e de contatos do contratante (ADR-0019) pertencem à relação comercial do Ziip e ficam
separados. Chamados, acesso assistido e acesso de emergência ficam no schema `support` (`AJUDA-SUPORTE-SOBRE.md` §4).

| Tabela | Conteúdo | Retenção |
|---|---|---|
| `metric_definition` | chave, unidade, tipo (gauge, counter), descrição, versão, dono | permanente |
| `metric_point` | chave, `tenant_id` nulo para global, valor, rótulos de lista fechada, `ts`; partição diária | 90 dias, `DROP` de partição |
| `metric_daily` | média, mínimo, máximo e contagem por chave, tenant e dia | 24 meses |
| `alert_rule` | métrica, comparador, limiar, janela, leituras consecutivas, severidade, escopo, ligada, `snooze_until`, dono | permanente, versionada |
| `alert_event` | regra, tenant, severidade, aberto, reconhecido por, resolvido, canais notificados | 24 meses após resolver |
| `plan`, `tenant_subscription` | plano, módulos, cotas e vigência por tenant | permanente |
| `feature_flag` | chave, tenant, valor, tipo (plano ou rollout), dono, data de remoção | até a remoção |
| `onboarding_step` | tenant, passo do §6.1, status, referência ao aceite | permanente |
| `tenant_lifecycle` | estado, motivo (inclusive inadimplência contratual, com a fatura vencida de referência), justificativa, aviso enviado em, executor e papel, fator de MFA do step-up, fim do contrato, carência até, contestação, cancelado em | permanente |
| contrato e afins | ADR-0019: contrato (com a política de SLA de suporte), aditivo, empenho, reajuste, fatura e pagamento, com referência aos documentos da fatura e da NFS-e (ADR-0022) | prazo fiscal e contratual |
| `document_acceptance` | tenant, usuário (nulo no aceite do tenant), documento (termos, privacidade, contrato, DPA), versão, hash do texto, data, IP | prazo contratual |
| `deployment` | versão, commit, ambiente, início e fim da implantação do software (ADR-0021) | permanente |
| `audit_event` | ator, papel, ação, alvo, antes e depois sem dado pessoal, `prev_hash`, `hash` (SHA-256 encadeado) | piso do ADR-0015 |
| `config_item` | briefing, radar, marcadores do coletor | permanente |

O estado do tenant (`PROVISIONING`, `ONBOARDING`, `ACTIVE`, `SUSPENDED`, `OFFBOARDING`, `ERASED`) fica em
`tenant_catalog.status`, que já usa `ACTIVE`; `tenant_lifecycle` guarda a história. Na interface, os estados aparecem
em português (provisionando, em implantação, ativo, suspenso, em saída, eliminado).

## 5. Coletores

Um job `central-collector` no processo `worker`, a cada 5 min, com trava por advisory lock (uma execução no cluster),
watchdog próprio (alerta se a última coleta passar de 15 min) e consolidação diária com expurgo.

| Origem | Como a Central obtém |
|---|---|
| Latência, erros, JVM, Hikari, filas | Micrometer com tag `tenant_id`, exportado ao OTel Collector (RNF-50); a Central lê resumos e burn rate na API do backend de métricas |
| Outbox, `job_runs`, `pix_inbox`, fiscal, retornos | funções `platform_stats.*` em cada shard, `SECURITY DEFINER`, que devolvem só contagens e idades por `tenant_id` |
| Migrações, shards, porte | `tenant_schema_version` e `tenant_catalog` |
| Parâmetros e prazos | `parameter_versions` via função agregadora (chave, `valid_to`, status), nunca o valor |
| Backup, WAL, TLS, uptime | API do provedor e da ferramenta de backup (ADR-0011); sonda externa por webhook autenticado |
| Segurança | eventos do Keycloak e contadores da API (login, 429 de busca por documento) |

Regras de alerta: alerta ativo não redispara; fecha sozinho quando a condição limpa; soneca e desligamento auditados;
persistência mínima de duas leituras para fontes externas, como no coletor do Ziip Cloud.

## 6. Console da plataforma

### 6.1 Implantação

1. Cadastro no catálogo como `PROVISIONING`, com porte, modo de isolamento e shard (ADR-0005).
2. Provisionamento idempotente pela outbox: estrutura, DEK no cofre, organização e convite do primeiro administrador.
3. Plano, módulos e cotas a partir do contrato (ADR-0019).
4. Checklist de parametrização de `PARAMETROS-DO-TENANT.md` §5, passo a passo, com o bloqueio "B" de cada fase.
5. Aceite final com hash; só então `ACTIVE` (durante os passos 2 a 4, `ONBOARDING`). Produção recusa operação sem os parâmetros bloqueantes aceitos.

### 6.2 Suspensão

Sempre manual, com motivo, justificativa e aviso prévio registrado; executada por uma pessoa com MFA e step-up, sem
segundo aprovador: `PLATFORM_OWNER` por qualquer motivo, ou `PLATFORM_FINANCE` só por inadimplência contratual (§2).
Bloqueio por inadimplência (ADR-0019, item 5) tem o mesmo efeito e as mesmas regras. O produto nunca suspende
sozinho. Efeito: backoffice em somente leitura. Continuam funcionando, por se tratar de serviço essencial: portal do
consumidor (2ª via e Pix), recebimento de webhook Pix e de retornos bancários, baixa de pagamentos e exportação de
dados do cliente. Reativação, pelos mesmos papéis que podem suspender pelo motivo registrado, desfaz o efeito e é
auditada. A suspensão do SaaS não altera nenhuma regra de corte do consumidor (ADR-0015, item 3).

### 6.3 Saída do cliente

1. Pedido registrado; estado `OFFBOARDING`; novas operações de faturamento bloqueadas a partir da data acordada.
2. Exportação em formato aberto em até 15 dias, com manifesto e hash por arquivo (RNF-42).
3. **Carência de 30 dias**, contada do fim do contrato (decisão do dono do produto, 12/10/2026): o cliente baixa a
   exportação, confirma o recebimento e pode contestá-la (arquivo faltante ou divergente). Download, confirmação e
   contestação ficam registrados. Contestação aberta impede a execução até ser respondida; a resposta corrige a
   exportação ou reagenda a eliminação, com registro.
4. Eliminação agendada só por `PLATFORM_OWNER`, com MFA, step-up e justificativa, e executada só depois do fim da
   carência, durante a qual pode ser cancelada com registro; o sistema recusa execução antes do fim da carência.
   Execução: `DROP SCHEMA` ou remoção por `tenant_id` no schema compartilhado, seguida de destruição da DEK
   (crypto-shredding, RNF-43); backups expiram em até 35 dias.
5. Eliminação concluída e certificado de descarte emitido em até 30 dias após o fim da carência, ou seja, até 60 dias
   do fim do contrato (RNF-42). O certificado traz data, escopo, hashes, prazo de expiração dos backups e das cópias
   imutáveis (ADR-0015 item 5, ADR-0022) e a exceção dos chamados de suporte, retidos pela vigência do contrato mais 5
   anos (ADR-0020); estado `ERASED`; métricas do tenant permanecem só como agregados anônimos.

### 6.4 Área do tenant: contrato com o Ziip

Visão do lado do prestador, dentro da aplicação do tenant (ADR-0022): histórico de faturas e pagamentos do contrato
SaaS, com download da fatura paga e da NFS-e da venda em PDF e XML. Acesso do `ADMIN` do tenant e de papel próprio de
consulta do contrato; leitura filtrada pelo `tenant_id` do token. O XML é o autorizado, conferido pelo hash.

## 7. Acesso assistido de suporte

O ADR-0020 substitui a impersonação prevista na primeira versão deste documento. Regras completas em
`AJUDA-SUPORTE-SOBRE.md` §5; resumo:

1. Chamado aberto e pedido com motivo, escopo (leitura, ou leitura e escrita) e prazo (padrão 2 h, máximo 72 h).
2. Consentimento do administrador do tenant (ou de quem tiver `support.authorize-access`), dado dentro do produto. Não
   há acesso sem consentimento, salvo a exceção de emergência abaixo.
3. O agente entra com a própria identidade e MFA, com o consentimento no token; não existe login como o usuário. A API
   recusa consentimento expirado ou revogado.
4. Bloqueados mesmo com escopo de escrita: revelar documento, exportar, alterar usuário, MFA ou parâmetro aceito.
5. Faixa fixa na tela do agente e do tenant; todas as requisições vão para o `audit_log` do tenant com o id do
   consentimento e para `audit_event`; o tenant vê as sessões, pode revogar qualquer uma e recebe o relatório.

**Exceção de emergência** (ADR-0020, seção Exceção; decisão do dono do produto, 12/10/2026). Sem consentimento do
tenant, só quando houver incidente de segurança declarado e registrado (plano P3.10):

1. Só `PLATFORM_OWNER`, com reautenticação e segundo fator no momento de abrir a sessão; MFA de login anterior não
   basta.
2. Somente leitura, com as mesmas vedações do acesso assistido (revelar documento, exportar); qualquer escrita é
   recusada.
3. A sessão referencia o incidente, não um consentimento, e termina quando o incidente é encerrado ou antes.
4. Aviso ao tenant (administrador e encarregado) em até 24 h da abertura da sessão, pela outbox, com motivo, período
   e escopo; o tenant vê a sessão na mesma lista do acesso assistido.
5. Toda requisição vai para o `audit_log` do tenant e para `audit_event` com o id do incidente; a sessão, o motivo e o
   que foi consultado entram no registro do incidente.

## 8. API de métricas estável

Prefixo `/api/v1/platform/metrics` (a versão é a da API, plano P0.10), só leitura, autenticação de serviço (client credentials e mTLS) para consumo
externo, além dos papéis de plataforma.

| Rota | Retorno |
|---|---|
| `GET /catalog` | definições de `metric_definition` |
| `GET /snapshot` | último valor de cada chave, global e por tenant |
| `GET /series?key=&tenant=&from=&to=&step=5m\|1d` | série; `1d` vem de `metric_daily` |
| `GET /alerts?state=open` | alertas ativos |
| `GET /health-scores` | score e fatores por tenant |

Envelope: `schema_version`, `product` (`ziip-san`), `instance`, `collected_at` e `points`, cada ponto com `key`,
`tenant_ref`, `value`, `unit` e `labels`. Chaves no formato `domain.object.measure` (ex.: `fiscal.queue.oldest_age_s`).
Mudança aditiva na mesma versão; remoção ou mudança de significado só em `v2`, com convivência de seis meses (mesma
regra do plano, §8). `tenant_ref` é o UUID do catálogo, nunca nome ou documento.

## 9. O que se aproveita do Ziip Cloud

| Aproveita (conceito, reescrito em Java) | Não aproveita | Por quê |
|---|---|---|
| Série de 5 min com 90 dias e agregado diário com 24 meses | Chave e valor livres (`CentralConfigItem`) para regras | regra precisa de tipo, versão e dono |
| Alerta ativo até normalizar, sem reenvio; soneca; liga e desliga auditados; e-mail, push e webhook | Coletor como `BackgroundService` em cada processo | várias réplicas coletariam em dobro; aqui um job com trava |
| Health Score com fatores explicados, "3 coisas para hoje", tendências, briefing, modo TV | Verificação de MFA pela flag `MfaAtivo` do cadastro | o ADR-0007 exige o fator usado na sessão (`amr`) |
| Watchdog do coletor e SLO de 30 dias | Ações de `/api/gestor/tenants/*` sem a checagem de 2FA da Central | toda a superfície de plataforma segue a mesma política |
| Kit de evidências em `.zip` com SHA-256 gravado na trilha | Leitura cruzada com `IgnoreQueryFilters`, e-mail e IP do cliente em Riscos e Conta 360, IP no kit | a Central não lê dado pessoal; só funções agregadoras |
| Radar atualizado por agentes via `POST` auditado | Trial, Asaas, cartão, reembolso, régua, bloqueio automático por expiração | cliente público contrata por licitação (ADR-0019) |
| Conciliação pagamento × licença; conta de sistema e de teste fora das métricas | Funil, coortes, site, promoções, tour | venda B2C self-service, sem equivalente aqui |
| Telemetria de host somente leitura | Script `systemd` numa VPS | depende da hospedagem (ADR-0011); usar API do provedor |

## 10. Segurança

- **Isolamento.** Role `ziipsan_central` sem `SELECT` em tabela de tenant; só `EXECUTE` nas funções `platform_stats.*`,
  cada uma com teste que prova a ausência de coluna pessoal no retorno. Teste de fronteira (P1.13) impede o módulo da
  Central de importar módulos de domínio. A rota de plataforma recusa token com `tenant_id`.
- **Rate limit.** 60 requisições por minuto por operador; exportações e kit limitados a 5 por hora; 429 auditado.
- **Auditoria.** `audit_event` encadeado por SHA-256, verificado a cada 6 h com alerta crítico em quebra.
- **Sessão.** Inatividade de 15 min; step-up nas ações destrutivas; alerta em login de plataforma de dispositivo novo.
- **Logs.** Mesmo filtro de dado pessoal do RNF-52.

## 11. Fases e aceite

| Entrega | Fase | Critérios de aceite |
|---|---|---|
| Central v0 (P1.17) com console v0 (P1.6) | P1 | rota de plataforma sem `amr` → 403 `mfa_obrigatorio`; `SELECT` de `ziipsan_central` em tabela de tenant falha no teste; abas Visão geral (fatores medidos), Plataforma, Segurança e Alertas; alerta abre e fecha sem duplicar; implantação com bloqueio "B"; suspensão manual por uma pessoa com MFA (`PLATFORM_OWNER`, ou `PLATFORM_FINANCE` só por inadimplência contratual), com o backoffice só leitura e o portal, o Pix e os retornos funcionando (teste); financeiro com outro motivo ou pedindo eliminação recebe 403; API v1 com catálogo e teste de contrato |
| Abas Faturamento e Fiscal (P2.11) | P2 | junto de P2.1 e P2.6; ciclo atrasado e fila fiscal acima do RNF-08 disparam alerta em homologação |
| Contratos e financeiro (P2.12–P2.14, ADR-0019 e ADR-0022) | P2, antes da assinatura do piloto (P3.9) | fatura gera NFS-e na produção restrita; nenhum caminho de código bloqueia tenant sozinho (teste); alertas de vencimento e de cota; tenant baixa a fatura paga e a NFS-e em PDF e XML e não vê contrato de outro tenant |
| Central completa (P3.15–P3.17) | P3 | Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar, Suporte; falha provocada alerta (P3.11); acesso assistido sem consentimento negado e auditado; acesso de emergência só com incidente declarado, `PLATFORM_OWNER` reautenticado, somente leitura e aviso ao tenant em até 24 h; saída de tenant sintético dentro do RNF-42, com carência de 30 dias cancelável, eliminação só por `PLATFORM_OWNER` e certificado em até 30 dias após a carência; kit com hash na trilha |

# P3.5 — Notificações com prova de entrega — relatório de entrega

Base: HEAD `2ba51f8` de `mastersonmdaniel/Ziip_San` (pacotes locais da P3.36 e da
P2.5, mesmo HEAD). Pacote LOCAL conferido, **sem push** (conforme a tarefa).
Nenhuma asserção afrouxada; nenhum dado inventado (ausência = erro explícito).
Sem Java/Maven nesta VM: **nada foi compilado nem executado** — a prova é a CI
total no PC do dono.

## Bloqueio honesto do espelho (ler primeiro)
A 1ª chamada via GitHub MCP (`get_file_contents` de `platform/ziipsan-platform`)
devolveu **HTTP 429 (rate limit do provedor)** — parada dura respeitada: nenhuma
outra chamada ao GitHub foi feita. A implementação foi construída sobre os
pacotes locais `~/workspace/ziipsan-producao/p3.36/entrega/` (base das
preferências/central) e `p2.5/entrega/` (média/crítica de consumo do metering),
com proveniência em `../espelho/PROVENIENCIA.md`. **Não** foram espelhados:
interface de e-mail da P0.45, motor de modelos da P2.30, catálogo real da P1.38
e cadastro de contatos da P1.3 — a entrega integra com eles por **portas**
(interfaces), sem inventar assinaturas deles, no mesmo padrão da P3.36.

## Migration — NENHUMA (declaração expressa)
Conferidos a spec integral da P3.5 e o mapa Flyway do plano v3.0 §3, linha a
linha: **não há versão reservada à P3.5** — o aceite não cita versão (ao
contrário das vizinhas: P3.36 = tenant `V1_15`, P3.4 = tenant `V9_2`, onde o
mapa inclui "prova de entrega" do aviso de corte) e o mapa não tem linha da
P3.5. Seguindo a instrução da tarefa, **nenhuma migration foi criada**.
Consequências honestas (pendências 1 e 2 abaixo): a persistência entra pela
porta `DeliveryStore` (sem JDBC inventado) e as definições dos parâmetros
`msg.*`/`alert.*` no catálogo global aguardam versão reservada.

## Onde vive
Módulo `platform/ziipsan-platform`, pacote `br.com.ziipsan.platform.notification`
— o MESMO pacote da P3.36, reutilizando sem cópia: `NotificationChannel`,
`NotificationCategory`, `NotificationPreference`, `NotificationPolicy` (piso de
categorias obrigatórias), `NotificationParameters`, `BusinessEventCatalog`,
`CatalogEvent`, `OutboxMessage` e o padrão de recusa da `NotificationIngestRule`.
JSON somente via classe `Json` do core; erros via `ApiException` do core.

## Arquivos criados (17 de produção + 3 de teste = 20)
- **Tipos/regra de prova:** `EmailEventType` (delivered/bounced/complained),
  `DeliveryStatus`, `DeliveryAttempt` (tentativa + prova, chave = id da outbox),
  `EmailDeliveryEvent` (id do provedor = chave de idempotência),
  `DeliveryProofRule` (prova, portão do corte, alinhamento de domínio, taxa de
  reclamação < 0,10%), `SuppressionPolicy` (`msg.suppression_policy`, classe b).
- **Portas:** `EmailGateway` + `SenderDomainStatus` (interface de e-mail da
  P0.45; provedor NÃO decidido — LC.2), `MessageTemplateEngine` (motor da
  P2.30), `ContactRegistry` (cadastro da P1.3: resolve contato, marca inválido,
  abre pendência), `PreferenceReader` (leitura do registro da P3.36),
  `MessagingParameters` (`msg.email_from_domain` d, `msg.suppression_policy` b,
  `msg.channels_enabled` d, `alert.high_consumption_pct`, `alert.channels` d),
  `DeliveryStore` (tentativas, supressão, pausa e métricas — tudo por tenant).
- **Disparo:** `NotificationDispatchRule` (catálogo da P1.38 + carga via `Json`:
  evento fora do catálogo = 409; campo obrigatório ausente = 422),
  `DispatchResult` (resultado nomeado por canal, incl. fronteiras),
  `NotificationDispatchService` (guardas em ordem declarada; eventos do
  provedor com efeitos idempotentes; portão do corte; alerta de consumo).
- **Alerta:** `HighConsumptionAlertRule` (limite = média × (1 + pct/100);
  dispara só ESTRITAMENTE acima).
- **Testes (22 casos):** `DeliveryProofRuleTest` (6), `HighConsumptionAlertRuleTest`
  (4), `NotificationDispatchServiceTest` (12, serviço real sobre fakes das portas).

## Cobertura do acceptance, item a item
| Item | Estado | Como |
|---|---|---|
| Aviso de corte sem prova de entrega bloqueia a OS (teste) | ✅ | `DeliveryProofRule.assertCutoffAllowed` (409) + `service.assertCutoffAllowed` (consumo da P3.4); testes: sem tentativa/só SENT/BOUNCED/COMPLAINED bloqueiam, DELIVERED libera |
| Prova de entrega por canal, dos eventos delivered/bounced/complained da interface de e-mail da P0.45 | ✅ | `onEmailEvent` resolve a tentativa pelo id do provedor e grava a situação; bounce/complaint contam como NÃO entregue |
| Mensagens pelo motor de modelos da P2.30 | ✅ (porta) | `MessageTemplateEngine.render` por `templateCode` da carga; versão do modelo gravada na tentativa; nenhum leiaute próprio criado |
| Disparadas por eventos do catálogo da P1.38 | ✅ | `NotificationDispatchRule` + `BusinessEventCatalog` (porta da P3.36); fora do catálogo/versão = 409, nenhum disparo (teste) |
| Preferências por canal e finalidade lidas da P3.36; aviso legal fora do alcance da preferência | ✅ | `PreferenceReader` sobre o registro da P3.36 + `NotificationPolicy.isMandatory` (piso ∪ tenant, via porta `NotificationParameters` da P3.36); teste: BILLING desligado não envia, LEGAL_NOTICE com linha desligada envia |
| E-mail por tenant: domínio com SPF/DKIM/DMARC alinhados (`msg.email_from_domain`, classe d) | ✅ | guarda `assertDomainAligned` antes de todo envio (domínio conferido = configurado; os 3 alinhados); teste por desalinhamento |
| Reputação, métricas, pausa e supressão isoladas por tenant (`msg.suppression_policy`, classe b); tenant ruim não interrompe os demais (teste) | ✅ | `DeliveryStore` sempre endereçado por tenant; testes: A pausado × B envia; supressão de A × B envia; pausa automática de A por reclamação × B envia |
| Requisitos de remetente em massa Gmail/Yahoo/Microsoft (SPF, DKIM, DMARC e reclamação < 0,10%) | ✅ | alinhamento (acima) + taxa: 9/10.000 = 0,09% ok, 10/10.000 = 0,10% viola ("abaixo de"); violação pausa só o tenant |
| Provedor decidido na LC.2 (SES sa-east-1 como referência) — fronteira | ✅ fronteira | só a interface `EmailGateway` é modelada; nenhum provedor/SDK no código |
| Rejeição/reclamação marca contato inválido na P1.3, abre pendência e conta como não entregue | ✅ | `onEmailEvent`: `markContactInvalid` + `openInvalidContactPendency` + supressão conforme a política; idempotente por `providerEventId` (teste de reentrega sem efeito novo) |
| Canais ligados por `msg.channels_enabled` (classe d) | ✅ | primeira guarda do disparo; teste CHANNEL_DISABLED |
| Remetente autenticado SMS/voz (MP 1.396/2026) — regra da ED-41, fronteira | ✅ fronteira | declarado; nenhum envio SMS/voz aqui |
| Alerta preventivo de consumo anormal: crítica da P2.5 acima de `alert.high_consumption_pct` da média, antes da fatura, canais de `alert.channels` (classe d), orientação de teste de vazamento + atalho de revisão por protocolo (teste com e sem disparo) | ✅ | `onReadingClosed` + `HighConsumptionAlertRule`; calculado à mão: média 10, pct 50 → limite 15; 16 dispara (excesso 60,00%, variáveis `leakTestGuidanceCode`/`billReviewActionCode` conferidas), 15 e 14 não disparam (mapa vazio, zero envios) |
| SMS, WhatsApp, push e custo por mensagem (ED-41) — fronteira | ✅ fronteira | canal PUSH devolve `FRONTIER_ED41` nomeado; nada é simulado |
| Alerta por telemetria de fluxo contínuo (ED-17) | fora de escopo | não tocado, como manda o aceite |

**Resumo: 12 itens atendidos (3 deles como fronteira/porta, por desenho do
aceite), 0 parciais de regra, 2 pendências de fiação/persistência abaixo.**

## Pendências e incertezas honestas
1. **Persistência sem migration (a maior):** sem versão reservada, `DeliveryStore`
   é só porta — não há JDBC, tabela nem índice nesta entrega. A integração
   precisa reservar a versão (faixa do módulo platform ou sequência global
   pós-P3.12) e implementar a porta; a prova do corte da P3.4 (V9_2) e esta
   porta precisam pousar na mesma história de tenant. Até lá, o serviço roda
   sobre a implementação que a integração ligar (os testes usam fake em memória).
2. **Parâmetros sem registro no catálogo:** as chaves `msg.email_from_domain`,
   `msg.suppression_policy`, `msg.channels_enabled`, `alert.high_consumption_pct`
   e `alert.channels` estão fixadas (chave + classe) na porta
   `MessagingParameters`, mas as definições em `parameter_definitions` (core)
   também dependem de versão reservada — não foi criado SQL por conta própria.
3. **Convenção de carga:** `eventVersion`, `templateCode`, `purposeCode`,
   `recipientUserId`/`recipientCustomerId` e `variables` dentro da carga são
   convenção DESTA tarefa (mesma declaração da P3.36) até o catálogo da P1.38
   ser espelhado; o nome de evento do alerta (`reading.closed`) idem — a
   integração o declara no `vertical.json`. A média do alerta é recebida pronta
   da crítica da P2.5 (quem calcula é ela); esta tarefa só aplica o percentual.
4. **Janela da taxa de reclamação:** apurada sobre o histórico gravado do tenant
   (`Instant.EPOCH` até agora). A janela oficial é a do provedor escolhido na
   LC.2 — quando ele existir, `countSent/countComplaints` ganham a janela dele.
5. **Revisão cruzada:** a revisão item a item foi feita nesta mesma frente
   (o agente está em profundidade 2/2, sem poder acionar o subagente revisor
   previsto no fluxo); recomenda-se a revisão cruzada do agente principal antes
   do push, como nas tarefas anteriores.
6. **Fiação de beans/portas:** nada instancia o serviço nem liga as portas às
   implementações reais (worker da P1.9 chamando `dispatch`/`onEmailEvent`,
   webhook do provedor chamando `onEmailEvent`, P3.4 chamando
   `assertCutoffAllowed`) — nasce na integração, como nas demais tarefas.

## Riscos para a CI
- **Jackson 3 (`tools.jackson`):** `JsonNode.properties()` usado na leitura das
  variáveis — conferir contra a versão do Boot do módulo (a P3.36 só usava
  `get/asText`); se a API divergir, o ajuste é local em `NotificationDispatchRule`.
- **Pacote compartilhado com a P3.36:** esta entrega pressupõe os arquivos da
  P3.36 no mesmo pacote (`NotificationPolicy`, `NotificationParameters`,
  `BusinessEventCatalog`, `CatalogEvent`, `OutboxMessage`,
  `NotificationPreference`, `NotificationChannel/Category`) — os dois pacotes
  precisam viajar juntos ou a P3.36 precisa já estar no repo.
- `SourceHygieneTest`/checagens de marca do módulo platform sobre os arquivos novos.
- Sem JDK/Maven nesta VM, os 22 testes foram conferidos por leitura contra os
  padrões espelhados, não executados — a 1ª rodada de CI é a prova real.

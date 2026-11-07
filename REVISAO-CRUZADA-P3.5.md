# P3.5 — Revisão cruzada independente — "Notificações com prova de entrega"

Revisor: agente revisor independente (1ª revisão do pacote; a entrega veio sem
revisão). Método: leitura integral dos 19 arquivos de produção + 3 de teste
contra o `acceptance` da P3.5 (`tarefas-P3.json`), os arquivos reais da P3.36
(mesmo pacote `notification`), a classe `Json` e `ApiException` do core
(espelho da P3.36) e precedentes de Jackson 3 nos espelhos do repo. **Sem
JDK/Maven nesta VM: revisão estática — nada foi compilado nem executado; a CI
total no PC do dono continua sendo a prova de compilação/teste.** Nenhum push
foi feito.

## Veredito: **LIBERA COM CORREÇÕES APLICADAS**

O núcleo do aceite está correto e bem testado (portão do corte, isolamento por
tenant, taxa de reclamação calculada à mão, alerta com/sem disparo). Foram
encontrados **2 defeitos reais na máquina de estados da prova** — um deles
permitia FABRICAR prova de entrega de aviso não entregue — e 1 defeito menor de
robustez; os três foram corrigidos nesta revisão, com 3 testes novos (22 → 25).
As pendências restantes são as de fiação/persistência já declaradas no
relatório da entrega (inerentes ao aceite, que não reserva migration) mais 2
lacunas de idempotência registradas abaixo para a integração — nenhuma delas
invalida as regras entregues.

## Tabela item a item do acceptance

| # | Item do acceptance | Estado | Evidência / nota |
|---|---|---|---|
| 1 | Aviso de corte sem prova de entrega BLOQUEIA a OS (teste) | ✅ Coberto | `DeliveryProofRule.assertCutoffAllowed` (409 `cutoff-notice-not-delivered`) + `NotificationDispatchService.assertCutoffAllowed` (consumo da P3.4). Testes: `DeliveryProofRuleTest.cutoffBlockedWithoutProof` (sem tentativa / só SENT / BOUNCED / COMPLAINED bloqueiam; DELIVERED libera) e `NotificationDispatchServiceTest.deliveredEventGrantsProof` (fluxo completo). |
| 2 | Prova de entrega registrada por canal, dos eventos `delivered`/`bounced`/`complained` da interface de e-mail da P0.45 | ✅ Coberto (com correção) | `EmailEventType` (só os 3), `EmailGateway` como porta da P0.45, `DeliveryAttempt` carrega o canal e o `providerMessageId` de correlação; `onEmailEvent` resolve por (tenant, providerMessageId). **Correção 1** abaixo: a sobrescrita livre de estados permitia prova falsa. Ressalva menor: `hasProof`/`assertCutoffAllowed` não filtram por canal (ver pendência 5). |
| 3 | Mensagens montadas pelo motor de modelos da P2.30 | ✅ Coberto (porta) | `MessageTemplateEngine.render(tenant, templateCode, channel, variables)`; nenhum leiaute próprio no pacote; código+versão do modelo gravados na tentativa e conferidos no teste do caminho feliz (`templateVersion == 3` vindo do fake do motor). Sem duplicação de motor. |
| 4 | Disparadas por eventos do catálogo da P1.38 | ✅ Coberto no fluxo principal / ⚠️ parcial no alerta | `NotificationDispatchRule.draft` exige evento+versão no `BusinessEventCatalog` (porta da P3.36, reutilizada sem cópia): fora do catálogo = 409 e nenhum disparo (teste). **Exceção:** o alerta de consumo monta o `DispatchDraft` direto no serviço, sem passar pelo catálogo (pendência 3). |
| 5 | Preferências por canal e finalidade lidas da P3.36; aviso legal FORA do alcance da preferência | ✅ Coberto | `PreferenceReader` (leitura do registro da P3.36) + `NotificationPolicy.isMandatory` REAIS da P3.36 (piso ∪ tenant via `NotificationParameters.tenantMandatoryCategories`) — não reimplementado. Semântica "qualquer linha desligada desliga" confere com o `planIngest` da P3.36. Teste `preferenceOffAndLegalNoticeOverride`: BILLING desligado → `PREFERENCE_OFF`; LEGAL_NOTICE com linha desligada → envia. |
| 6 | E-mail por tenant: domínio de envio do tenant (`msg.email_from_domain`, classe d) com SPF/DKIM/DMARC alinhados | ✅ Coberto | Porta `MessagingParameters.emailFromDomain` (chave+classe fixadas); guarda `DeliveryProofRule.assertDomainAligned` ANTES de todo envio: domínio conferido == configurado (case-insensitive) e os 3 alinhados. Teste por cada desalinhamento + domínio trocado (`domainAlignment`) e via serviço (`DOMAIN_NOT_ALIGNED` sem DKIM). **Correção 3:** `domainStatus == null` virava exceção crua; agora é `DOMAIN_NOT_ALIGNED` nomeado. |
| 7 | Reputação, métricas, pausa e supressão isoladas por tenant (`msg.suppression_policy`, classe b); tenant com lista ruim não interrompe os demais (teste) | ✅ Coberto | `DeliveryStore` endereçado por tenant em TODAS as operações; `SuppressionPolicy` (bounce/complaint) como record da política. Testes de isolamento (3): A pausado × B envia; supressão de A × B envia para o mesmo endereço; pausa automática de A por reclamação × B envia. |
| 8 | Requisitos de remetente em massa Gmail/Yahoo/Microsoft: SPF/DKIM/DMARC + taxa de reclamação **abaixo de** 0,10% | ✅ Coberto | Item 6 + `DeliveryProofRule.complaintRate/complaintRateBreaches`: `>= 0,0010` viola ("abaixo de", não "até"). Calculado à mão e testado nos dois lados da fronteira: 9/10.000 = 0,09% ok; 10/10.000 = 0,10% viola; 1/100 viola. Violação pausa só o tenant infrator (em `dispatch` e em `onEmailEvent`). Janela de apuração = histórico gravado (pendência declarada 4 da entrega — janela oficial é do provedor da LC.2). |
| 9 | Provedor de e-mail decidido na LC.2 (SES `sa-east-1` como referência) — fronteira | ✅ Fronteira respeitada | Só a interface `EmailGateway` é modelada; nenhum provedor/SDK no código. **Sem invasão de escopo.** |
| 10 | Rejeição/reclamação marca o contato como inválido na P1.3, abre pendência e conta como aviso NÃO entregue | ✅ Coberto (com correção) | `onEmailEvent` (BOUNCED/COMPLAINED): `markContactInvalid` + `openInvalidContactPendency` + supressão conforme a política; status BOUNCED/COMPLAINED não é prova (`hasProof` só aceita DELIVERED) — testado, inclusive com idempotência por `providerEventId` (reentrega sem efeito novo). **Correções 1 e 2** tratam dos eventos terminais DISTINTOS sobre a mesma mensagem e da ordem da marca de processado. |
| 11 | Canais ligados por `msg.channels_enabled` (classe d) | ✅ Coberto | Primeira guarda de canal do disparo (`CHANNEL_DISABLED`, testado). O caminho do alerta também passa pela mesma guarda via `dispatchDraft`. |
| 12 | Remetente autenticado SMS/voz (MP 1.396/2026) — regra da ED-41 | ✅ Fronteira respeitada | Apenas declarado (javadoc/`FRONTIER_ED41`); nenhum envio SMS/voz. **Sem invasão.** Observação menor: para PUSH fora de `channels_enabled`, o motivo devolvido é `FRONTIER_ED41` (a checagem de fronteira precede a de canal ligado) — nome do motivo discutível, sem efeito funcional. |
| 13 | Alerta preventivo de consumo anormal: no fechamento da leitura, crítica da P2.5 acima de `alert.high_consumption_pct` da média ⇒ aviso antes da fatura, canais de `alert.channels` (classe d), orientação de teste de vazamento + atalho de revisão por protocolo (teste com E sem disparo) | ⚠️ Parcial | Regra pura correta e calculada à mão: `HighConsumptionAlertRule` — limite = média × (1 + pct/100), disparo ESTRITAMENTE acima (10/50% → limite 15; 16 dispara com `excessPct = 60.00`, 15 e 14 não disparam, mapa vazio e zero envios — testes na regra E no serviço). Variáveis `leakTestGuidanceCode`/`billReviewActionCode` conferidas no teste. A média é RECEBIDA da crítica da P2.5 (não recalculada — sem duplicação). **Parcial por:** (a) o rascunho do alerta não passa pelo catálogo da P1.38 (item 4); (b) sem idempotência por leitura (pendência 3); (c) "antes da fatura" é garantia do chamador (o gatilho é o fechamento), não algo que o código prove. |
| 14 | SMS, WhatsApp, push e custo por mensagem (ED-41) — fronteira | ✅ Fronteira respeitada | Canal não-e-mail/não-IN_APP → `FRONTIER_ED41` nomeado, nada simulado (testado com PUSH). IN_APP → `DELEGATED_TO_CENTRAL` (P3.36). Nota: o enum `NotificationChannel` da P3.36 só tem IN_APP/EMAIL/PUSH — SMS/WhatsApp nem existem como canal neste módulo, o que é coerente com a fronteira. **Sem invasão.** |
| 15 | Alerta por telemetria de fluxo contínuo (ED-17) | ✅ Fora de escopo respeitado | Não tocado. **Sem invasão.** |
| — | **Migration: a P3.5 NÃO tem versão reservada — qualquer migration no pacote = bloqueio** | ✅ Conforme | Conferido o pacote inteiro: **zero arquivos `.sql`, zero diretório de migration** (19 `.java` de produção + 3 de teste + relatório). Persistência pela porta `DeliveryStore`, como declarado. |

## Correções aplicadas nesta revisão

### Correção 1 — CRÍTICA: transição de estado livre fabricava prova falsa e duplicava pendências
- **Arquivo:** `NotificationDispatchService.java` (`onEmailEvent`), com javadoc de
  `DeliveryStatus.java` ajustado.
- **Era:** `onEmailEvent` resolvia a tentativa para o status de QUALQUER evento
  que chegasse, sem olhar o status atual — embora o javadoc de `DeliveryStatus`
  dissesse "muda UMA vez", nenhuma guarda impedia a sobrescrita. Consequências
  reais: (a) bounce registrado e, depois, um `delivered` tardio/duplicado (outro
  `providerEventId`, mesma mensagem) sobrescrevia para DELIVERED →
  `hasProof == true` → **OS de corte liberada para aviso que nunca foi
  entregue**, derrotando o item central do aceite; (b) `delivered` seguido de
  `bounced` apagava prova válida; (c) dois eventos terminais distintos
  (bounce + complaint) chamavam `markContactInvalid`/`openInvalidContactPendency`
  DUAS vezes — pendência duplicada no cadastro da P1.3. A idempotência existente
  cobria só a reentrega do MESMO `providerEventId` (único caso testado).
- **Ficou:** vale o primeiro evento terminal; tentativa já resolvida não regride
  nem repete efeitos (o evento novo é marcado como processado e a tentativa
  vigente é devolvida). Única transição posterior admitida:
  DELIVERED → COMPLAINED — a reclamação pressupõe a entrega e, pelo texto do
  aceite, conta como não entregue e dispara os efeitos no cadastro (supressão,
  invalidação, taxa). O javadoc de `DeliveryStatus` foi reescrito para dizer a
  regra que o código agora impõe.
- **Testes novos (3)** em `NotificationDispatchServiceTest`:
  `deliveredAfterBounceDoesNotCreateProof`,
  `bounceAfterDeliveredIsIgnored`,
  `complaintAfterDeliveryAppliesEffectsAndErasesProof` — todos sobre o serviço
  real com os fakes existentes, nos padrões da casa.

### Correção 2 — `markEventProcessed` antes dos efeitos perdia os efeitos para sempre em caso de falha
- **Arquivo:** `NotificationDispatchService.java` (`onEmailEvent`).
- **Era:** a marca de "evento processado" era gravada logo após `updateAttempt`,
  ANTES de invalidar o contato/abrir pendência/suprimir. Se qualquer efeito
  lançasse exceção, a reentrega do provedor cairia no atalho de idempotência e
  os efeitos NUNCA aconteceriam — contato válido para sempre, sem pendência.
- **Ficou:** a marca é a ÚLTIMA operação do método (depois de cadastro,
  supressão e pausa). Falha no meio ⇒ reentrega tenta de novo. O teste de
  idempotência existente (`bouncedEventEffectsAreIdempotent`) continua válido:
  no caminho feliz a marca continua gravada ao fim da 1ª aplicação.

### Correção 3 — `domainStatus == null` do gateway virava exceção crua em vez de motivo nomeado
- **Arquivo:** `NotificationDispatchService.java` (`dispatchDraft`).
- **Era:** se a implementação do gateway devolvesse `null`,
  `DeliveryProofRule.assertDomainAligned` lançava `IllegalArgumentException`
  não tratada (o `catch` só cobria `ApiException.Validation`) — o disparo
  quebrava em vez de devolver o resultado nomeado que todas as outras guardas
  devolvem.
- **Ficou:** guarda explícita devolvendo `DispatchResult.Outcome.DOMAIN_NOT_ALIGNED`
  com o motivo "o provedor não devolveu o estado de autenticação do domínio".

### Correção 4 (documentação de porta) — semântica de `countSent`/`countComplaints`
- **Arquivo:** `DeliveryStore.java` (só javadoc, sem mudança de assinatura).
- **Era:** "tentativas enviadas" era ambíguo; uma implementação JDBC que
  contasse só tentativas ainda em `SENT` encolheria o denominador a cada
  resolução e inflaria a taxa de reclamação (pausando tenants bons).
- **Ficou:** javadoc fixa a semântica — `countSent` = tentativas com `sentAt`
  no período, qualquer que seja a situação atual; `countComplaints` =
  resolvidas como COMPLAINED com `resolvedAt` no período. (O fake dos testes
  já se comporta assim na janela EPOCH→agora usada pelo serviço.)

## Padrões do projeto — conferência

- **JSON só via `Json` do core:** ✅ único ponto de leitura é
  `Json.readTree` em `NotificationDispatchRule`; o teste monta a carga com
  `Json.write`. Nenhum `ObjectMapper`/parse manual. O uso de
  `tools.jackson.databind.JsonNode` (`properties()`, `asText()`,
  `isTextual()`, `isIntegralNumber()`) tem precedente no código real do repo
  (espelhos GIS/P2.21 usam `node.properties()`; core usa `asText`/`isTextual`)
  — o risco Jackson 3 declarado no relatório está, por evidência dos espelhos,
  mitigado; a CI confirma.
- **Nenhum dado inventado / ausência = erro explícito:** ✅ verificado —
  contato ausente/não verificado → `NO_CONTACT`; evento de mensagem
  desconhecida neste tenant → 404; campo obrigatório da carga ausente → 422
  nomeando o campo; evento fora do catálogo → 409; média/pct ausentes → erro;
  `complaintRate` sem envios → erro (a violação, no serviço, é tratada à parte:
  sem envios não há taxa nem pausa — correto). Endereço nunca vem da carga.
- **Parâmetros do tenant pelo mecanismo do projeto:** ✅ nenhum valor de
  parâmetro hardcoded; tudo via portas `MessagingParameters`
  (`msg.email_from_domain` d, `msg.suppression_policy` b,
  `msg.channels_enabled` d, `alert.high_consumption_pct`, `alert.channels` d) e
  `NotificationParameters` da P3.36. Constantes do código são códigos de
  modelo/finalidade/evento (`alert.high-consumption`, `guidance.leak-test`,
  `action.request-bill-review`, `reading.closed`, `HIGH_CONSUMPTION_ALERT`) —
  convenção declarada da tarefa, não parâmetro disfarçado.
- **Reuso sem duplicação:** ✅ `NotificationPolicy`, `NotificationParameters`,
  `BusinessEventCatalog`, `CatalogEvent`, `OutboxMessage`,
  `NotificationPreference/Channel/Category` são os arquivos REAIS da P3.36,
  referenciados — nenhum motor de modelos, catálogo ou registro de
  preferências reimplementado. Os dois pacotes precisam viajar juntos (ou a
  P3.36 já estar no repo), como o relatório declara.
- **Testes calculados à mão, não circulares:** ✅ taxa (9/10.000, 10/10.000,
  1/100), alerta (10/50% → 15; 16/15/14; excesso 60,00%), pausa por reclamação
  (99 sementes DELIVERED + 1 envio = 100; 1/100 = 1%). As sementes são
  tentativas gravadas direto na porta, não saídas do próprio código sob teste.
  Total após a revisão: **25 testes** (6 + 4 + 15).
- **Nenhuma asserção afrouxada:** ✅ nenhuma asserção existente foi tocada; as
  correções ADICIONAM guarda e testes.
- **Compilação provável:** ✅ conferidos, contra os espelhos, todos os imports,
  ordens de componentes dos records (`DeliveryAttempt`, `DispatchDraft`,
  `NotificationPreference`, `RenderedMessage`), assinaturas das portas da
  P3.36 (`NotificationPolicy.isMandatory(category, Set)`,
  `PreferenceReader.find(tenant, customer, user, purpose, category, channel)`
  — ordem de parâmetros consistente entre porta, serviço e fake) e o
  balanceamento sintático dos arquivos alterados. Sem JDK, a prova final é a CI.

## Defeitos e pendências (não corrigidos — decisão/integração)

1. **Persistência sem migration (declarada na entrega, inerente ao aceite):**
   `DeliveryStore` é só porta. A integração precisa reservar a versão e
   implementar o JDBC pousando na mesma história de tenant da prova do corte
   da P3.4 (V9_2). Recomendação desta revisão para essa implementação:
   constraints únicas em `(tenant_id, provider_event_id)` e
   `(tenant_id, provider_message_id)` — são elas, no banco, que fecham a
   corrida entre duas entregas concorrentes do mesmo webhook (a guarda em
   memória do serviço é sequencial, não atômica).
2. **Parâmetros `msg.*`/`alert.*` sem registro no catálogo global**
   (declarada): dependem da mesma versão reservada do item 1.
3. **Alerta de consumo fora do catálogo e sem idempotência:** o rascunho é
   montado no serviço com evento `reading.closed`/categoria `ACCOUNT` como
   convenção (declarada) e `noticeId = UUID.randomUUID()` a cada chamada —
   reprocessar o fechamento da mesma leitura DUPLICA o e-mail de alerta, e a
   categoria não vem do catálogo (se o catálogo declarar outra, preferência/
   obrigatoriedade saem erradas). Correção estrutural sugerida à integração:
   rotear o alerta pela outbox (P1.9) como os demais avisos, ou receber o id
   da leitura/ciclo na assinatura de `onReadingClosed` e deduplicar por ele.
   Não reescrevi: muda assinatura e contrato do gatilho da P2.5 (interpretação,
   não defeito objetivo pontual).
4. **`dispatch` sem deduplicação por (tenant, aviso, canal):** a reentrega
   at-least-once da mesma `OutboxMessage` pelo worker da P1.9 geraria segundo
   envio e segunda tentativa (a P3.36 deduplica a ingestão por
   `source_message_id`; aqui não há guarda equivalente usando
   `attemptsForNotice`). Não corrigi porque a semântica de reenvio legítimo
   (ex.: novo aviso após bounce sai com outro id de outbox?) não está
   especificada no aceite — a integração deve garantir a entrega única do
   worker ou especificar a guarda.
5. **Prova do corte sem filtro de canal:** `hasProof` aceita DELIVERED de
   qualquer canal do aviso. Hoje só EMAIL gera tentativas (IN_APP é da central,
   demais são fronteira), logo é equivalente; quando a ED-41 ligar outros
   canais com tentativa própria, a P3.4 deve dizer se a prova exigida é a do
   e-mail — registrar lá.
6. **Arestas menores (observações, sem correção):**
   (a) `openInvalidContactPendency` recebe `customerId == null` quando o
   destinatário é usuário interno — a implementação da P1.3 deve aceitar ou a
   porta deve explicitar; (b) `DeliveryAttempt` aceita status terminal com
   `resolvedAt == null` e `SENT` sem `providerMessageId` — o serviço garante
   no caminho real, mas o record sozinho não impõe; (c) a janela da taxa de
   reclamação é o histórico inteiro (`Instant.EPOCH`→agora), pendência já
   declarada na entrega (janela oficial é do provedor da LC.2).

## Resumo

Pacote correto no essencial e honesto nas fronteiras (LC.2, ED-41, ED-17 sem
invasão; nenhuma migration criada). A revisão encontrou e corrigiu o defeito
que mais importava — a prova de entrega podia ser fabricada por um evento
`delivered` tardio após `bounced`, liberando corte indevido — mais a perda
silenciosa de efeitos por marca de processado prematura e a exceção crua de
domínio nulo, com 3 testes novos cobrindo as transições. **LIBERA COM
CORREÇÕES APLICADAS**, restando as pendências de integração/persistência
acima, das quais a mais importante é a implementação do `DeliveryStore` com
as constraints de unicidade recomendadas.

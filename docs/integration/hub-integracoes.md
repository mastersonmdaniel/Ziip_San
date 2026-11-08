# Hub de integrações (P3.37, PLT-20)

Superfície de operação de TODAS as integrações externas do Ziip SAN,
no módulo `integration/ziipsan-integration`, pacote
`br.com.ziipsan.integration.hub`. Nenhum endpoint, URL ou credencial de
terceiro é presumido aqui: terceiros entram por adaptadores da porta
comum, ligadas pela fiação.

- **Porta comum** — `ExternalConnector`: PSP, bancos, SEFAZ, NFS-e,
  WhatsApp, e-mail, SMS, birôs, CENPROT, gráfica e ERP implementam a
  mesma porta (`name`, `kind`, `partnerVersion`, `testConnection`,
  `send`, `homologationLayoutFingerprint`). Conectores específicos
  (ERP Betha/IPM/Fiorilli etc.) são da ED-62, sobre esta porta.
- **Credenciais** — por tenant, só a REFERÊNCIA circula:
  `integration.<name>.credential_ref` (classe d) aponta para o cofre da
  P0.45 (`CredentialVault`); o segredo é resolvido pelo adaptador na
  chamada, nunca pelo painel/log/fila. Rotação devolve referência nova
  (`rotate`); o parâmetro do tenant só muda pelo fluxo de aceite da
  P1.6. `CredentialMonitor` deriva VALID/EXPIRING/EXPIRED/
  NO_EXPIRY_DECLARED/NOT_FOUND com janela de alerta padrão de 30 dias
  (fronteira inclusiva) — é dele que sai o alerta de expiração.
- **Modo por tenant e integração** — `integration.<name>.mode`
  (classe d): SIMULATED não chama o parceiro, SANDBOX fala com
  homologação, PRODUCTION só opera com o aceite do tenant conferido em
  `IntegrationParameters.operableMode` (o modo gravado, sozinho, não é
  aceite).
- **Despacho** — `HubDispatchService`, nesta ordem: replay idempotente
  (mesma chave devolve o guardado, sem nova chamada) -> SIMULATED ->
  limite de taxa (`IntegrationRateLimiter`, balde por par
  tenant/integração) -> circuito (`CircuitBreaker`: padrão 5 falhas/
  60 s; a instância do despacho é configurável) -> tentativas pela
  `RetryPolicy` padrão (limite 10 s por tentativa — imposto pelo
  adaptador HTTP da fiação —, 3 tentativas, recuo 500 ms dobrando até
  8 s, só para UNAVAILABLE/exceção; REJECTED é final) -> falha final
  enfileira e conta UMA falha no circuito. Cada despacho gera o
  `IntegrationCallLog` com `correlation_id` e carga mascarada.
- **Log mascarado** — `PayloadMasker`: chaves sensíveis (segredo,
  token, senha, credencial, documento, cartão...) viram `***` em
  qualquer profundidade; cadeia com `Bearer ` idem; carga que não é
  objeto JSON é omitida por inteiro, com o tamanho. Leitura/escrita só
  pela classe `Json` do core.
- **Fila de falhas e tela de reprocessamento** —
  `FailureQueueService`: a mensagem guarda a carga original (só dentro
  da fila); a visão da tela (`FailedMessageView`) é mascarada.
  Reprocessar repete a mesma chave de idempotência e o mesmo
  `correlation_id`; sucesso resolve; o teto de tentativas da mensagem
  (despacho + reprocessos) a torna DEAD, e morta não reprocessa.
- **Teste de contrato diário** — `ContractTestService`: por entrada do
  catálogo (versão do parceiro), pede a impressão do leiaute ao
  ambiente de HOMOLOGAÇÃO (contexto SANDBOX forçado) e compara com a
  esperada cadastrada: MATCH, DRIFT (mudança de leiaute) ou NOT_RUN
  com motivo — ausência de prova nunca vira MATCH. Cadência de 24 h
  em `isDue`; o agendamento é da fiação.
- **Catálogo de parceiros (GES-11)** — `PartnerConnectorCatalog`:
  nome, família, parceiro, versão, capacidades, impressão esperada e,
  para PSP, a matriz de comportamento da P3.2 como projeção
  (`PspBehaviorView`, mesmos campos de `PspPixPort.PspBehavior`) —
  o hub lê a matriz daqui (`pspMatrix()`), sem depender do módulo
  collection. PSP sem matriz não entra no catálogo.
- **Painel de saúde** — `IntegrationHealthPanel`: situação derivada
  por regra única (DOWN: circuito aberto ou credencial
  expirada/ausente; DEGRADED: meio-aberto, fila pendente, credencial
  expirando/sem expiração declarada, DRIFT; UNKNOWN: nenhum sinal —
  ausência nunca é "saudável"; UP: sucesso sem sinais). A visão da
  Central usa a aba `integracoes` (`/central/integracoes`, REFERRAL da
  P3.15) e traz a pior situação por integração; a do console do tenant
  filtra pelo tenant.

## Persistência (pendência nomeada)

O §3 do plano v3.0 reserva para a faixa `integration` apenas a `V7_1`
(P2.26); a linha `V7_x` cita a P3.37 SEM número. Por isso esta entrega
NÃO traz migration aplicada: a minuta das tabelas do hub (fila de
falhas, idempotência, resultados de contrato) está em
`V7_X-DRAFT__integration_hub.sql`, ao lado deste documento, FORA de
`db/migration` — ela só vira migration quando a integração reservar o
slot no §3 (COE-15). Até lá, as portas `FailureStore` e
`IdempotencyStore` rodam com as implementações em memória entregues
(ambiente local/teste, espírito da P0.45).

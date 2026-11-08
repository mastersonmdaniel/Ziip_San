# ED-95 — Perdas aparentes: nota de integração

**Pacote:** `br.com.ziipsan.core.apparentloss` em `shared/ziipsan-core`
(núcleo puro, sem JPA/controller — padrão ED-24/ED-96). SQL em
`db/pendente/` com prefixo `ED-95__`, sem número Flyway (atribuir o
próximo slot livre da sequência global na integração).

## Fiação pendente (nomeada)

1. **P-ED95-01 — Adaptadores das portas (`LossPorts`):**
   - `PropertyRegistrySource`: conector municipal concreto sobre o
     hub de integrações (P3.37, camada da ED-62). O leiaute do
     cadastro de cada ente (campos, códigos de uso) é declaração do
     ente na integração — nenhum leiaute foi inventado. O mapeamento
     dos códigos municipais de uso para `PropertyUse` também é
     declaração da integração [A DEFINIR].
   - `UnitFactsPort`: montar o retrato de P1.3 (unidade/categoria/
     área), P2.23 (ligação/ramal e situação), ED-14 (troca de
     hidrômetro, vazões nominal/pico) e ED-17 (telemetria, quando
     houver).
   - `WorkOrderPort`: motor de OS da P1.31. O tipo da OS de
     fiscalização segue a proposta `INSPECTION` da ED-24 — pendência
     O9-5 do dono (confirmar ou substituir na declaração dos tipos
     do tenant). Vistoria e recadastramento em lote idem: declarar
     os códigos dos tipos no tenant.
   - `LossStore`: JDBC sobre as 5 tabelas `loss_*`. **Correção D4
     (correção-2 da revisão):** a proveniência NÃO vem mais "do
     `HumanDecision` que acompanha a chamada" — ela viaja dentro do
     modelo: `Finding` carrega `decidedBy`/`decidedAt` (decisão e
     descarte) e `Suspicion` carrega
     `decidedBy`/`decidedAt`/`inspectionWorkOrderId`; o adaptador
     grava as colunas `decided_by`/`decided_at`/
     `inspection_work_order_id` desses records. Idem
     `evaluation_channel`: é o campo `Finding.evaluationChannel`,
     modelado e travado em `BACKOFFICE` no record (o adaptador grava
     o campo; nenhum valor é fixado por fora do modelo).
   - `TenantParameters`: registro de parâmetros do core (P1.6).
2. **P-ED95-02 — Parâmetros:** numerar `ED-95__core_loss_parameters.sql`
   (4 parâmetros, todos sem fábrica) e `ED-95__apparent_loss.sql`;
   conferir na integração a UNIQUE composta de `consumer_units`/
   `service_connections` se um dia se quiser FK física para elas
   (esta entrega não a declara de propósito).
3. **P-ED95-03 — IT de banco:** `ApparentLossIT` (padrão da ED-96)
   roda na CI total do PC do dono contra a pilha real; nesta VM a
   prova equivalente foi a aplicação real em PG 18.6 descartável
   (ver relatório).
4. **P-ED95-04 — Tolerância de área do cruzamento:** o aceite não
   nomeia chave nem tolerância. Hoje ela chega explícita ao
   `runCrossMatch`; se o dono quiser parametrizar, registrar a chave
   no catálogo na integração (não inventar aqui).
5. **P-ED95-05 — Chave do programa de autodenúncia:**
   `fraud.self_report_fine_waiver_enabled` é CHAVE PROPOSTA desta
   entrega [A DEFINIR] — confirmar ou renomear no catálogo.
6. **P-ED95-06 — Domínio de `registry.property_registry_sync`:**
   nenhuma fonte enumera os valores; o modo é gravado verbatim.
   Fechar o domínio (ex.: nomes dos modos de sincronização do hub)
   é ato da integração com a fonte do hub.

## Fronteiras que a fiação NÃO pode cruzar

- Nenhum adaptador pode ligar saída de regra/achado a débito, corte
  ou auto de infração: a API não tem essa porta (teste de
  arquitetura em `NoAutomaticDebitOrCutTest`). Auto e débito são da
  ED-24 (`infraction.charge_timing`, defesa e decisão antes).
- O cruzamento só pode ser exposto em tela/rota de BACKOFFICE
  (ADR-0034, GIS-CADASTRO §12). Fora dele, imóvel × ligação só
  agregado (regra do GIS, `gis.public_aggregation_min_units`, de
  outra entrega).
- ML continua fora: o `MlGate` só avalia prontidão; construir
  modelo exige decisão posterior sob o ADR-0042 com as regras
  medidas (relatório de acerto por regra).
- P3.9 intocada; protesto de CDA (ED-31) desligado — nada aqui o
  toca. Item 32 (arquitetura tarifária alvo) não executado.

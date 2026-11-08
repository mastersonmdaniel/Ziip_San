# Pacote ISO 45001 — ED-71 (módulo `certification`)

Saúde e segurança ocupacional sobre o núcleo de certificações da P1.32
(ADR-0041), no core (`shared/ziipsan-core`), subpacote
`br.com.ziipsan.core.certification.health45001`, ao lado dos irmãos
`quality9001` (ED-69) e `infosec27001` (ED-73). Nenhum módulo novo:
nasce só o subpacote.

## Ativação

- Parâmetros reais do core V3: `cert.iso45001.enabled` (BOOLEAN, classe
  d, padrão `false`), `cert.iso45001.edition` (ENUM, ALLOWED_VALUES
  `["2018"]`, padrão `"2018"`) e escopo declarado
  `cert.scope_statement`. Edição-alvo única: **ISO 45001:2018**.
- `Iso45001Activation` é componente de TODOS os registros do pacote
  (construtor canônico exige ativação ligada — padrão reforçado da
  ED-73): sem a norma ligada, nenhum registro nasce, por fábrica ou por
  construtor. Proposta ≠ aceite no nascimento da ativação.
- Nova edição, quando publicada e incorporada ao parâmetro, entra pela
  comparação de cláusulas (`OhsClauseAssessment.compareEditions`).
- **Desligar não apaga registro**: não há operação de exclusão no
  código nem DELETE no SQL pendente
  (`cert.deactivation_keeps_records` travado em `true` no núcleo).

## Peças

| Classe | O que é |
|---|---|
| `Iso45001Activation` | Régua de ativação sobre os parâmetros reais do core V3 |
| `HazardRiskEntry` | Inventário de perigos/riscos por atividade e local, escopo por área da P3.30 (`orgUnitId` → `org_units`), escala de risco declarada pelo tenant, campo de risco psicossocial |
| `OsSafetyGate` | Análise de risco da tarefa + permissão de trabalho como etapas da OS de risco; portão de início com bloqueios nomeados; canal ED-36 (backoffice) × ED-20 (campo), mesma régua |
| `PpeDelivery` | Entrega de EPI pela ED-58 com vínculo a trabalhador e função, lote + CA, ciência do trabalhador |
| `AccidentInvestigation` | Investigação sobre a ocorrência da P2.25; dado de saúde em componente/tabela separados, lido só pela porta com política + finalidade, com livro de acessos |
| `OhsClauseAssessment` | Mapa de cláusulas como autoavaliação do cliente; comparação de edições |

## Fronteiras (o que este pacote NÃO duplica)

- **P1.31 (OPE-14/RAS-25):** os fatos de segurança da OS já vivem em
  `service_order_safety`, `service_order_crew` (habilitação do executor:
  `qualification_code` + validade) e `service_order_entry_permits`
  (V9_1). O portão consome esses fatos por referência; os códigos de
  habilitação são os do catálogo de SST do tenant
  (`sst.required_controls_by_service_type`), inclusive a NR-10 (código
  `NR-10`) para trabalho elétrico. Nenhum texto de NR é reproduzido.
- **ED-58:** estoque, requisição, reserva, baixa e lote/CA são do
  almoxarifado; a entrega de EPI referencia item, lote e requisição.
- **P2.25:** a investigação liga-se à ocorrência única
  (`cert_occurrence`) por FK composta; não há segundo registro de
  incidente.
- **P3.30:** o escopo por área é a unidade organizacional (`org_units`
  da V1_8), por FK composta — o filtro obrigatório de leitura/escrita
  por escopo continua sendo da P3.30, aplicado na fiação.

## Sem fábrica presumida

Nenhum perigo, atividade, local, EPI, fator psicossocial, matriz ou
limiar de risco é semeado: catálogos e escala são do tenant. A régua só
impõe forma (valores dentro da escala declarada, ao menos um controle,
coerência dos campos) e o portão de início.

## Licença da norma

Textos e títulos literais de cláusulas da ISO 45001 **não** são
reproduzidos: `literal_title` nasce e permanece `[A DEFINIR]`, travado
por CHECK no SQL e sem caminho de preenchimento no código, até a
decisão do dono sobre licença × reescrita (pendência nomeada no
relatório). O mapa guarda referência (número), resumo em palavras
próprias e a autoavaliação do cliente, com o aviso fixo de que
autoavaliação não é certificação.

## Dado de saúde (LGPD)

O detalhe de saúde fica em `cert_h45001_accident_health_data` (tabela
própria), fora da tabela da investigação; a única leitura é
`AccidentInvestigation.readHealthDetail(política, acessante,
finalidade, instante)`, que nega acessante fora da política declarada
e registra cada leitura autorizada em
`cert_h45001_health_access_log`. `toJson()` da investigação nunca
inclui o detalhe — só o marcador `healthDataPresent`.

## SQL

`db/pendente/ED-71__certification_health_iso45001.sql` — 9 tabelas,
sem número global (regra do brief). Na integração: próximo número livre
da sequência global da P3.12 (V100+), atualizar
`migration-policy/published-tenant-migrations.txt` e mover para
`db/migration/tenant/`. Toda FK composta aponta para pai com
`UNIQUE (tenant_id, id)` (lição da cascata C3).

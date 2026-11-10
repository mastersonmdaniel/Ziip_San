# II-1 — OS por clique no elemento GIS: notas do mecanismo

**Âmbito:** só o MECANISMO/núcleo (decisão 64). A TELA do clique no mapa é a pendência P-II1-TELA (decisão 57).

## O que o clique faz

1. O operador clica num elemento GIS (nó ou arco da topologia persistida GIS-03).
2. `ClickServiceOrderService.propagate` carrega o retrato pela porta existente da GIS-11 (`ManeuverPorts.SnapshotPort`) e o `ClickPropagationEngine` calcula, conforme o modo da intervenção:
   - `DOWNSTREAM` / `UPSTREAM` / `BOTH` — traçado dirigido do `ManeuverGraph` (GIS-11), sem limite; com `maxHops`, BFS por profundidade sobre a mesma adjacência dirigida;
   - `ISOLATION` — área isolável, válvulas a fechar e UCs da `IsolationEngine` (GIS-11), sem reimplementação.
3. UCs afectadas: conexões funcionais (SG-852) cujo nó de derivação ou arco cai no conjunto afectado, na ordem do aviso da GIS-11 (sensíveis, depois essenciais, depois código), UCs sem duplicados.
4. `buildPayload` monta a OS pré-preenchida (elemento, propagação, ligações, UCs, economias, essenciais/sensíveis) com os alvos do núcleo P1.31: elemento como `GIS_POINT`/`GIS_LINEAR` e cada UC como `CONSUMER_UNIT`. `submit` só chama a porta (`ClickOrderPort`) com o payload pronto; a fiação traduz para `ServiceOrderService.createOrder` — a máquina de estados da OS não é duplicada.

## Desfechos tipificados (nunca erro silencioso)

- `WITH_AFFECTED_UNITS`, `WITHOUT_AFFECTED_UNITS` (expandiu, sem UCs), `ISOLATED_ELEMENT` (não alcança nenhum outro elemento), `NOT_ISOLABLE` (modo ISOLATION, com o motivo da IsolationEngine).
- Elemento fora do retrato: `IllegalArgumentException` nomeada.

## Parâmetros — nenhum número inventado

- `maxHops` e `maxAffectedUnits` são configuráveis; `null` = sem limite. As omissões de prova (`proofDefaults`) são construção de prova declarada. Valores de produção: `[A DEFINIR]` pelo tenant.
- Tipo de serviço, prioridade e prazo: do catálogo/tipo do tenant — nunca nascem neste pacote. Sem tipo, o payload nasce não-pronto com a falta nomeada (`serviceTypeCode [A DEFINIR]`).
- Truncagem (limite de saltos ou teto de UCs) é DECLARADA no resultado e BLOQUEIA a submissão: nenhuma OS nasce de propagação parcial em silêncio; a lista de UCs nunca é cortada — vem inteira com a truncagem assinalada.

## SQL

Nenhum: o mecanismo é cálculo puro sobre o retrato em memória; não há tabela nova nem migration tocada.

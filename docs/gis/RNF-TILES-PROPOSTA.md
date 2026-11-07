# GIS-06 — metas de desempenho de tiles: proposta ao RNF

**Origem:** GIS-CADASTRO-TECNICO §10.4. As metas abaixo são as
"metas propostas, a registrar no RNF pela tarefa da GIS-06 (não
vigentes antes disso)". Este documento é o registro da proposta e
da linha de base; a vigência depende de aceite no RNF pelo dono do
produto — esta tarefa não aprova o RNF.

## Metas propostas (§10.4)

| Medida | Meta proposta | Condição |
|---|---|---|
| Tile MVT — p95 com cache quente | ≤ 300 ms | tenant sintético com 1 milhão de feições |
| Tile MVT — p95 sem cache | ≤ 1 s | idem |
| Ficha da feição (identificação) | ≤ 500 ms | idem |
| Validação de área suja (até 1.000 feições) | ≤ 5 min | (GIS-03; registrada aqui por referência) |
| Isolamento em rede sintética de 5.000 km | ≤ 5 s | (GIS-11; por referência) |

## Como medir

`scripts/gis/measure-tiles.mjs` (Node, sem dependências) contra a
stack local: um pedido a frio e N a quente por tile, com p50/p95/
máx e o tamanho do corpo, gravados em JSON como linha de base
(`baseline-tiles.json`, fora do repo ou em docs/gis/baselines/ por
decisão da integração).

## Linha de base

**PENDENTE.** A medição exige a stack rodando com o tenant
sintético de 1 milhão de feições (massa da GIS-06 no ensaio local,
ADR-0033) — não executável na VM de produção desta tarefa (sem
JDK/Docker/navegador). O script está pronto e a primeira medição é
passo da integração/CI no PC do dono; nenhum número é afirmado
aqui sem medição.

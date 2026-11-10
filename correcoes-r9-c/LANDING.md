# LANDING — Lote R9-C (auditoria contínua, Rodada 9, 09/11/2026)

- **Coordenador do lote (sessão):** d96e85bd-2523-4b9d-aa56-27975297dfd7
- **Produtor (sessão):** 4e92c4e6-246f-4504-bef8-638594add3d5
- **Revisor (sessão):** 6b58596e-ab3d-4847-99e8-b6596bef2521 — **APROVADO COM RESSALVAS** (ressalva menor documental R9-C-01, sem acção exigida)
- **Confirmador (sessão):** 9f7c05fb-1e9f-4188-8059-2c3495338c52 — **CONFIRMADO** (0 divergências materiais; precisão de detalhe à R9-C-01: a frase do adendo falha em 3 dos 4 DisplayNames integrais, não em 2 dos 4 — sem acção exigida)
- **Data:** 09/11/2026

## Natureza do lote

Lote 100% documental. Cinco itens menores da Rodada 9, todos por adendo/errata/registo datado, sem reescrita de originais:

| # | Item | Artefacto (caminho no workspace) | SHA-256 |
|---|---|---|---|
| 1 | R9-C4-1 | `proposta-tarifaria/camada-4-campo/ADENDO-R9-C4-1-2026-11-09.md` | `45cb3ee6d7558beb5e0385a394174b6da04e618194fd9a7ae058312fc7f06dab` |
| 2 | R9-I-01 | `proposta-tarifaria/camada-2-maestro/ADENDO-R9-I-01-2026-11-09.md` | `ba241c13384dbba5cd6abc34eaa0cefc09a28e47b22acc3c3fe4a31eed0e7c71` |
| 3 | R9-I-02 | `base-ficticia-12k/ADENDO-R9-I-02-2026-11-09.md` | `2e3c86645a932d4c91660a4f19fff5bb3e494f08efeb4375a51f39bae6f1efd8` |
| 4 | R9-P2 | `correcoes-r9-c/REGISTRO-R9-P2-2026-11-09.md` | `09e433081dd3a437e6277105c2875dfbba57b5cf833f5e1fd0b4e5256b13d8ba` |
| 5 | R9-P3 | `multimunicipio/mm-05-fiscal/ADENDO-R9-P3-2026-11-09.md` | `65875c7167a54beb8c78c90bddb9e07dda5829e45230ad3e4c07772d99b9966a` |

Cadeia do lote (em `correcoes-r9-c/`):

| Artefacto | SHA-256 |
|---|---|
| `RELATORIO-R9-C.md` | `ab5c1aa9e61a4d5afc32c1e922ce6b7116d2f960a44deee73af924d1f1ca9fb1` |
| `REVISAO-R9-C.md` | `096c474e535c9fd82921a2cf88aa6b29814c35a8a1c7b53a3f308d3ab6b76411` |
| `CONFIRMACAO-R9-C.md` | `1ba202eafda6c4f49f7aec1421db5aabe00ef71bae66cf74b3957b9d10d6d501` |
| `LANDING.md` (este ficheiro) | `c451fa9e833d2125fd78a8a398211881a8323366a8720d20842c56313c7c4066` |

## Posição de pouso

Lote único, na fila pós-429, na posição que a sessão principal atribuir (depois dos lotes da base fictícia; ordem exacta a fixar pela principal — este lote não tem dependência de ordem com os demais lotes de correcção, salvo: o adendo R9-P3 viaja com o pacote MM-05 e **não desbloqueia** o MM-05; o bloqueio P-MM05-R8C-01 mantém-se vigente).

## Guardas no acto do pouso

1. **Rebase NÃO verificado** — GitHub em parada dura por HTTP 429; rebase contra a main corrente é OBRIGATÓRIO no acto, antes de qualquer push.
2. **Verificação pós-pouso por hash** — cada ficheiro da tabela acima conferido por SHA-256 contra os valores declarados; divergência ⇒ pouso não confirmado.
3. **Nada disto pousa código de produção** — são adendos, registo e cadeia documental. Nenhum original foi reescrito (RELATORIO-CAMADA-4.md, CORRECAO-CAMADA-2-R8C.md, exec.log, PORTAO-REGISTRO-MM-05.md, CONFIRMACAO-EXECUCAO-VARIANTE-T.md — hashes re-verificados pelo confirmador).
4. **P-MM05-R8C-01 mantém-se** — o adendo R9-P3 é remissão ao bloqueio, não desbloqueio. O MM-05 continua sem poder pousar até ao portão mecânico do ciclo R8-C + adendo de desbloqueio no LANDING do bloco.
5. **Ressalva R9-C-01** — menor, documental, sem acção exigida (frase de verificação do adendo R9-C4-1 sobre ocorrências de @DisplayName integrais; a conclusão 29/29 está provada por via independente).

## Estado do portão

Ver secção do PORTAO-REGISTRO-R9-C.md (saída mecânica executada pelo coordenador sobre os artefactos finais).

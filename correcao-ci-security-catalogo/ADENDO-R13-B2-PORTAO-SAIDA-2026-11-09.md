# Adendo R13-B2 — saída do portão arquivada (`portao-saida.txt`)

- **Data:** 2026-11-09 (escrita 17:02 UTC; 14:02 −03).
- **Produtor do lote R13-A (auditoria contínua, Rodada 13):** sessão `1c342896-6c61-42fb-b42a-c8eaeef0de4a`.
- **Pacote:** `correcao-ci-security-catalogo`.
- **Natureza:** adendo datado ao `PORTAO-REGISTRO-CORRECAO-CI-SECURITY-CATALOGO.md`.
  O registo original NÃO foi reescrito.

## O achado

O `PORTAO-REGISTRO-CORRECAO-CI-SECURITY-CATALOGO.md` afirma exit 0 sem
nenhuma saída do portão arquivada no pacote: antes desta correcção, um
`find -iname "*portao*"` no pacote devolvia só o próprio registo, e não
existia nenhuma saída da corrida original algures fora do pacote (busca
em `~/workspace/ziipsan-producao` só devolve saídas de outros pacotes;
o ZIP do pacote, `parches-security-catalogo-2026-11-09.zip`, também não
contém nenhuma saída do portão).

## A correcção

O portão foi **re-corrido** pelo produtor do lote R13-A, sem edições ao
verificador (`~/workspace/ziipsan-producao/esteira/portao-deploy.py`,
corrida de leitura de artefactos locais — NÃO é escrita no GitHub),
sobre o pacote-alvo, e a saída integral foi arquivada **dentro do
pacote** como `portao-saida.txt`.

## Resultado da corrida (re-corrida, 2026-11-09 17:01:18 UTC)

- **Exit: 0 — PORTÃO APROVADO.**
- REGRA 1 — REVISAO com veredito APROVADO: OK
- REGRA 2 — CONFIRMACAO confirmada/aprovada: OK
- REGRA 3 — Identidades distintas (cabeçalhos): OK
- REGRA 4 — Evidência de testes no RELATORIO: OK
- REGRA 5 — LANDING.md presente: OK
- REGRA 6 — Migrations na sequência/cobertura: OK
- Item (3) do protocolo (rebase contra a main corrente): NÃO VERIFICADO
  POR ESTE PORTÃO — verifica-se no acto do pouso, como o próprio registo
  já declara.
- Observação (não bloqueia): nenhuma migration .sql em `entrega/` —
  regra 6 vacuamente satisfeita.

## SHA-256 da saída arquivada

- Ficheiro: `portao-saida.txt` (neste pacote)
- SHA-256: `0d376f2f9711039668d515ec35d4c3d3176bca0e39e9571aeceb361b7e475d8e`

## Efeito

A afirmação de exit 0 do `PORTAO-REGISTRO-CORRECAO-CI-SECURITY-CATALOGO.md`
fica agora suportada pela saída arquivada, com o mesmo resultado (exit 0,
6/6 regras OK). Nenhum veredito se altera.

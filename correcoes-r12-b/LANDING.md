# LANDING — Lote R12-B (auditoria contínua, Rodada 12)

- **Coordenador (sessão):** `f482bdf7-e46f-465d-aa96-774f445737d0`
- **Data (UTC):** 09/11/2026.
- **Fonte:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-1143/00-CONSOLIDADO-RODADA12.md` §3.

## §1 — Posição na fila

Lote único, documental. Posição na fila pós-429 **a atribuir pela sessão principal**. Viajam como documentos do lote as duas erratas e a cópia de trabalho do Adendo G; o `REGISTRO-PLACAR-2D.md` viaja com o Adendo G — e com o Adendo H (manutenção da rodada 12:05, job `ziipsan-push-2h`), que está no mesmo ficheiro por ser o estado vivo do registro, não por este lote.

## §2 — Ficheiros e valores a conferir no pós-pouso

| Ficheiro | SHA-256 a conferir |
|---|---|
| `correcoes-r12-b/ERRATA-R12-REV-01-2026-11-09.md` | `682813ae0d3a762c7e0263ebcbe242a7306bbd357beae8cb9602b7ccca66ddbb` |
| `correcoes-r12-b/ERRATA-R12-B-01-2026-11-09.md` | `775e690654f7e9ae47d31614b6cb62d418a2a15045e9a7a77bdbf5f27b03a5e8` |
| `correcoes-r12-b/ADENDO-G-2026-11-09.md` (cópia de trabalho) | `9eeea4b75fe90249a00f5ea69a026f00bdec8ba536f623776c290332a28f57e4` |
| `correcoes-r12-b/LANDING.md` (este ficheiro) | ver guarda 2 abaixo |
| `correcoes-rg-lote3/REGISTRO-PLACAR-2D.md` | ver guarda 3 abaixo |

**Guarda 2 — valor a conferir para este ficheiro.** O valor SHA-256 a conferir no pós-pouso para este `LANDING.md` é o registado no `RELATORIO-CONSOLIDADO-R12-B.md` (lição do R11-EX-01: o LANDING declara no seu próprio texto qual o valor a conferir para si próprio). A tabela acima lista o valor do estado pré-preenchimento apenas onde ele existe; para este ficheiro, o valor a conferir é o do consolidado.

**Guarda 3 — registro.** O ficheiro vivo no acto do pouso pode ter novos adendos de manutenção anexados pelo job das 2h depois deste lote. A guarda é: a secção do Adendo G (linhas 831–883) tem de estar presente verbatim — texto a partir de `## G.`, SHA-256 `62dfcd4dd10f3031d81c459d531ede889b509577ca83ac28f0eb41d16de372fe` — e as primeiras 883 linhas têm de ter SHA-256 `1b58b0e1b2d91eea14e49074cdd2fe5a07d051e55a0fddb07a09b4358e9c5ad8`. O SHA-256 do ficheiro inteiro é recomputado no acto (corrente, 943 linhas: `5963a9e99a806f77bad076022032fa9f779512e5d9023854a98ff559c94924bf`).

## §3 — Guardas no acto do pouso

1. Rebase verificado contra a `main` corrente antes do push (item (3) do protocolo — C7).
2. Verificação pós-pouso por hash, ficheiro a ficheiro, contra a tabela do §2 e as guardas 2 e 3.
3. Divergência em qualquer valor ⇒ pouso NÃO confirmado; parar e voltar à sessão principal com a evidência.
4. Registar o pouso no diário do lote (commit do diário a declarar no relatório de pouso).

# RELATÓRIO CONSOLIDADO — Lote R12-B (auditoria contínua, Rodada 12)

- **Coordenador (sessão):** `f482bdf7-e46f-465d-aa96-774f445737d0`
- **Data (UTC):** 09/11/2026.
- **Fonte:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-1143/00-CONSOLIDADO-RODADA12.md` §3.
- **Workspace:** `~/workspace/ziipsan-producao/correcoes-r12-b/`.

## Veredito formal

**Lote R12-B CONCLUÍDO — produção CONCLUÍDA 2/2 · revisão APROVADO COM RESSALVAS (achado único R12-B-01, corrigido) · correcção CONCLUÍDA 1/1 · reapreciação APROVADO (0 divergências) · confirmação CONFIRMADO (0 divergências, 0 achados novos) · portão exit 1 só por 3 falsos positivos demonstrados (§3.2).** Apto a pousar na sua posição da fila pós-429, cumpridas as guardas do LANDING. Trabalho 100% local, zero chamadas ao GitHub; `DECISOES-DO-DONO.md` e o portão não editados.

## Cadeia (seis sessões distintas)

| Estágio | Sessão | Veredito |
|---|---|---|
| Produção | `892ec79f-752e-41fc-ae8b-209968286c0d` | 2/2 concluído |
| Revisão | `f0caa9d5-b169-4323-843c-6a12b7e1505e` | APROVADO COM RESSALVAS (R12-B-01) |
| Correcção | `233c6b3d-e7e7-4d45-9d7b-7364a8250a4d` | R12-B-01 corrigido por errata |
| Reapreciação | `9b87bb8a-cf72-42ec-b1a3-71b55b0211c0` | APROVADO (0 divergências) |
| Confirmação | `787b8f57-8f00-4eab-b317-f166f97f8752` | CONFIRMADO (0 divergências) |
| Coordenação | `f482bdf7-e46f-465d-aa96-774f445737d0` | Consolidado + portão |

## Item 1 — R12-REV-01 (menor): errata de uma linha

`correcoes-r11-a/PORTAO-REGISTRO-R11-A.md:24` dizia "Saída integral (lida na íntegra, 16 linhas)"; `portao-saida.txt` tem **18 linhas** (`wc -l` próprio do produtor, do revisor, do reapreciador e do confirmador; bloco citado `diff` byte-idêntico ao ficheiro; SHA `a5c33c7e…` = o do cabeçalho do registo). Só o número estava errado; sem efeito na substância nem no pouso do R11-A. Correcção: `ERRATA-R12-REV-01-2026-11-09.md` (16→18). Original intocado (mtime 12:58:35 UTC).

## Item 2 — R12-01 + R12-02 (menores): Adendo G ao REGISTRO-PLACAR-2D.md

O adendo F do registro dizia "162 das 180 na main, 18 aguardando push" (foto das 10:05). Com o pouso da P3.13 (commit `b17fce5`, verificação viva da mesa no consolidado §2.1 — HEAD, 2 ficheiros, hashes vivos `cc3e8975…` / `04169a03…` byte-idênticos aos da CONFIRMACAO-P3.13.md):

- Recálculo próprio do produtor, do revisor, do reapreciador e do confirmador, a partir da foto das 10:05: retomada na main 162 = ED 92 + P3 40 + P2 22 + P4 5 + GL 3; com a P3.13: P3 41, retomada **163**, total **263**, aguardando **17** (180−163; lista GL.4, ED-01, ED-51, LC.1–LC.14 = 1+1+1+14, todas membros da lista de 180 de `contagem_1005.json`).
- 263/286 = 91,958041958…% → **92,0%** a uma casa decimal por arredondamento — "91,9%" era truncagem (ocorre uma só vez em todo o registro, na linha 861, dentro do próprio G, citando-o para o corrigir).
- **Adendo G** anexado no fim do registro (linhas 831–883, 53 linhas, mesmo formato D/E/F), declarando 163/180 na main, 17 aguardando push, total 263/286, 92,0%. Integridade provada: SHA-256 das primeiras 830 linhas após o anexo = `a195140d…`, idêntico ao ficheiro pré-adendo; Adendo F intacto; **M2 inalterada (65/99/16/100), 0 classificações novas, 0 reclassificações**.

## Ciclo de correcção — R12-B-01 (menor, não bloqueante)

O RELATORIO §2 afirmava que "a secção G anexada é byte-idêntica à cópia de trabalho `ADENDO-G-2026-11-09.md`", mas o ficheiro tem 59 linhas contra 53 da secção — as 6 a mais são o cabeçalho próprio da cópia de trabalho. Só o texto a partir de `## G.` é byte-idêntico (SHA `62dfcd4dd10f3031d81c459d531ede889b509577ca83ac28f0eb41d16de372fe` nos dois lados — verificado por produtor, revisor, corrector, reapreciador e confirmador). Correcção: `ERRATA-R12-B-01-2026-11-09.md` (reformulação de uma linha). Nenhum original reescrito.

## Portão mecânico

- Primeira corrida: exit 1, 4 itens — Regra 3 (×3) + Regra 5 (LANDING.md ausente — lacuna REAL, não falso positivo).
- Segunda corrida (após a escrita do LANDING.md pelo coordenador): exit 1, só a Regra 3 (3 itens). Regras 1, 2, 4, 5 e 6: [OK].
- Os 3 itens da Regra 3 são falsos positivos demonstrados da classe (ii): as autorias estão declaradas nos cabeçalhos na forma `Rótulo (sessão):` e o extrator exige o rótulo imediatamente seguido de `:` — demonstração por chamada directa a `autor_do_cabecalho` (None nos três ficheiros; controlo sem o parêntese devolve o UUID real). Seis UUIDs distintos na cadeia. Demonstração completa em `PORTAO-REGISTRO-R12-B.md` §2. **Portão não editado** (SHA `05b58ba7…`, mtime 00:14:29 UTC). Veredito do portão em substância: **APROVADO**.

## Estado do registro (nota, não achado)

O `REGISTRO-PLACAR-2D.md` tem agora **943 linhas**: um Adendo H (manutenção da rodada 12:05, job `ziipsan-push-2h`) foi anexado depois da secção G (linhas 884–943). As primeiras 883 linhas continuam com o SHA `1b58b0e1…` e as primeiras 830 com `a195140d…` — **Adendo G intacto**. O bloco confirma explicitamente os valores do G (163/180, 263/286 = 92,0%, 17). O Adendo H não foi tocado por este lote. A foto das 12:05 confirma por leitura viva própria os 163 na main e os adendos G e H.

## Contexto chegado durante a tarefa (fora do âmbito deste lote)

A base fictícia foi **pousada por completo em 09/11/2026 ~12:24 −03** (lotes 193–262): `push/espera/state.json` done 262, head `fb9d346cdae444cdf7a2a266888bf294ea01233e` (verificado pelo coordenador em disco); `exec.log` regista FIM 262/262 às 15:22:36 UTC, verificação pós-pouso 315/315 ficheiros byte-idênticos e 308/308 SHA-256, e o diário commit `3edcbf7fec55eaa805fd47c9e76002442f609b78`. Isto não afecta este veredito — os valores confirmados dizem respeito ao pouso da P3.13 — mas altera o estado da fila para a Rodada 13.

## Artefactos finais (SHA-256, recomputados pelo coordenador)

| Artefacto | SHA-256 |
|---|---|
| `ERRATA-R12-REV-01-2026-11-09.md` | `682813ae0d3a762c7e0263ebcbe242a7306bbd357beae8cb9602b7ccca66ddbb` |
| `ERRATA-R12-B-01-2026-11-09.md` | `775e690654f7e9ae47d31614b6cb62d418a2a15045e9a7a77bdbf5f27b03a5e8` |
| `ADENDO-G-2026-11-09.md` (cópia de trabalho) | `9eeea4b75fe90249a00f5ea69a026f00bdec8ba536f623776c290332a28f57e4` |
| `RELATORIO-R12-B.md` | `2e06651d3716e8424a661fabb79a5844ad77bdfdbe5c8dd17630d13b6e1d7a91` |
| `REVISAO-R12-B.md` | `878862ed5ae987d141fd9cf1aa6b682fa18b4a089323db57ed2f4ef85bef823e` |
| `CORRECAO-R12-B-01-2026-11-09.md` | `5b9a0f6342de582434015cc17838b2806b5d096f23c326703755858af6393371` |
| `REAPRECIACAO-R12-B.md` | `2b4a944efdc9744b5996d6bd28d7d49a913a91851f7a0df5f4ffe515fb995091` |
| `CONFIRMACAO-R12-B.md` | `57d5433fd4a6b806e02c6e4440f72ba2f12c9bfb932525e02922955ffdb1130d` |
| `LANDING.md` | `3a6a50d4e8e3398afe5372baaaf568e4d529261e74086ffaeb19a52870558ff4` |
| `PORTAO-REGISTRO-R12-B.md` | `6f54d211e1c11e76677e311f627747e42b0cd7f0faced115285a977d4e4a5fb1` |
| `portao-saida.txt` (1.ª corrida) | `26ab6e4c34639cbc88ed00f59b221c9f3206429e8b18ecbc5db69c456c313b5a` |
| `portao-saida-2.txt` (2.ª corrida) | `5e8054e02b33c1dc964adb2cd196d51887e0ea2aa046b680cb5f0c5fac4714cd` |
| `RELATORIO-CONSOLIDADO-R12-B.md` (este ficheiro) | não auto-declarado — ver nota abaixo |

**Nota sobre o SHA deste ficheiro (lição do R11-EX-01).** Um ficheiro não pode declarar o seu próprio SHA final dentro de si mesmo: qualquer linha que o registe altera-o. Por isso este relatório **não** contém o seu próprio hash. O SHA-256 deste ficheiro, tal como escrito, é calculado pelo coordenador após a escrita e registado no relatório de entrega à sessão principal (registo externo ao ficheiro) — esse é o valor a conferir no pós-pouso. O valor `61c9334f…` que constou transitoriamente nesta tabela era o SHA do estado pré-registo e **não** é o valor a conferir.

## Pendências que sobrevivem (fora do lote)

- **C4-01** permanece aberta (decisão do dono pendente; não é condição de pouso da C4).
- **P-MM05-R8C-01** (bloqueio de pouso do MM-05) mantém-se.
- Limitação do extrator do portão (i)–(v) persiste; correcção só pelo rito próprio do portão.
- Pouso: lote único na fila pós-429; guardas no acto: rebase verificado + verificação pós-pouso por hash (LANDING §2–§3) — para o `LANDING.md` do R12-B, conferir o valor `3a6a50d4…`; para o registro, a guarda 3 do LANDING.

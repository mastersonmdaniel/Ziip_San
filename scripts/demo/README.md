# P3.23 — Ambiente de demonstração permanente e ensaio de POC

Aparato executável da P3.23 (plano v3.0): *ambiente local de demonstração
separado da homologação local (perfil de demonstração da P0.27); tenant de
demonstração com pelo menos 10 mil unidades fictícias geradas pela massa
sintética, sem dado pessoal real (documentos gerados marcados como
sintéticos; teste no gerador); recriação do zero por script, com tempo
medido; ensaio cronometrado contra o tempo de preparo que os editais de
referência concedem (5 e 7 dias úteis), com o resultado por item
registrado na matriz viva; critério de ensaio do P3: pelo menos 50 de 162
pontos no roteiro da VISAN e seções 2.4 e 2.5 do SAMAE sem falha;
relatório de cobertura (requisito, tarefa, teste e item de roteiro)
anexado ao aceite*.

**Nada disto foi executado contra o ambiente nesta entrega** (a VM de
produção do pacote não tem Docker/JDK/PostgreSQL): o que está provado
aqui é a lógica (testes abaixo). A recriação e o ensaio rodam no PC do
dono, junto da CI. A versão **na nuvem** do ambiente de demonstração é
da **LC.2** (IaC na AWS, antes da primeira POC de licitação) — não é
desta tarefa e nada aqui a antecipa.

## Como rodar (PC do dono, Git Bash, na raiz do repo)

### Recriar a demonstração do zero (tempo medido)

1. Ambiente: o perfil `demo` da P0.27 já existe (`scripts/local-env.sh
   demo`) — esta tarefa não cria outro compose nem outro perfil.
2. `cp scripts/demo/demo.env.example scripts/demo/demo.env` e preencha
   (tenant **próprio** da demonstração, diferente do de dev/homolog).
3. Confira o trecho `[CONFERIR-V3_1]` do `sql/10-seed-demo.sql` contra a
   V3_1 (`\d consumer_units` e a tabela de titulares) — é o único passo
   manual de schema; sem ele o orquestrador para na contagem de
   unidades, de propósito.
4. `bash scripts/demo/recriar-demo.sh` — sobe o perfil demo do zero
   (`--reset`), gera a massa, semeia, confere a contagem **medida** e
   grava `recriacao.json` com o tempo de cada etapa e o total.

### Ensaio cronometrado de POC

1. `bash scripts/demo/executar-ensaio.sh iniciar` — abre o cronômetro
   do tempo de preparo.
2. Prepare (recriação acima) e execute os roteiros da P1.25,
   preenchendo uma linha por item no formato de
   `docs/demo/resultados-ensaio.template.csv` (o resultado por item é o
   que alimenta a matriz viva — ver `docs/demo/MATRIZ-VIVA-E-ENSAIO.md`).
3. `bash scripts/demo/executar-ensaio.sh concluir resultados.csv
   [feriados.txt]` — fecha o cronômetro e avalia: VISAN ≥ 50/162, SAMAE
   2.4/2.5 sem falha e comprovadas, tempo dentro de 5/7 dias úteis e
   cobertura completa. Saída: `resultado-ensaio.json` +
   `cobertura-poc.csv`.

## Peças

| Arquivo | O que faz |
|---|---|
| `gerar-massa-demo.py` | Massa sintética determinística do tenant demo (≥ 10.000 unidades; titular e documento fictícios **marcados**; CSV + manifesto SHA-256) |
| `sql/00-preflight.sql` | Falha cedo: schema é o da demo, tabelas presentes, piso de 10 mil |
| `sql/10-seed-demo.sql` | Categorias `[CONFERIR-V3_1]`, esqueleto de unidades/titulares `[CONFERIR-V3_1]`, tarifa/grupos e medição em DDL verificada (V4_1/V4_3/V5_1, herdados da P2.9) |
| `sql/90-verificacao.sql` | Contagens pós-semeadura que entram no registro |
| `sql/99-limpeza.sql` | Remove a massa demo do tenant demo (confirmação explícita) |
| `recriar-demo.sh` | Orquestra a recriação do zero com guardas de separação/fiscal e tempos medidos por etapa |
| `executar-ensaio.sh` | Abre/fecha o cronômetro do ensaio (`iniciar`/`concluir`) |
| `avaliar-ensaio.py` | A régua do aceite: pontuação VISAN, SAMAE 2.4/2.5, dias úteis contra 5/7, cobertura; gera `resultado-ensaio.json` e `cobertura-poc.csv` |
| `testes/test_demo.py` | Testes da lógica (marcação sintética, piso, critério, dias úteis) — não são o ensaio |

No produto (Java, `platform/ziipsan-platform`, pacote
`br.com.ziipsan.platform.poc`): `PocEnsaioCriterion` (a mesma régua, com
os mesmos casos conferidos à mão) e `DemoTenantGuard` (as mesmas
guardas de separação/fiscal/piso/marcação). JSON sempre pela classe
`Json` do core.

## Provas feitas nesta entrega

- `python3 -m unittest discover -s scripts/demo/testes` → **15 testes
  OK**: piso recusado em 9.999; massa de 10.000 com 100% dos documentos
  marcados e DV conferido por reimplementação independente; determinismo
  por SHA-256; critério nos limites exatos (50 aprova, 48 reprova);
  falha e não-execução no SAMAE; estrutura VISAN divergente
  inconclusiva; 10 dias úteis fora dos dois orçamentos; cobertura
  incompleta reprovando; dias úteis calculados à mão (semana = 5,
  fração de dia, fim de semana = 0, feriado declarado = 0).
- Fumaça do CLI de `avaliar-ensaio.py` com massa de resultados de
  fixture: veredito APROVADO com 50/162 e 5,0 dias úteis, e
  `cobertura-poc.csv` gerado.
- `bash -n` nos dois scripts shell OK; guardas de falha fechada
  exercitadas (`concluir` sem marcador → erro; `recriar-demo.sh` sem
  `demo.env` → erro).

## Limites honestos

- **DDL da V3_1 (registry/titulares) não verificada** — mesma pendência
  registrada pela P2.9: o trecho de unidades/titulares do seeder é
  esqueleto marcado e o orquestrador para sem a contagem; a massa
  canônica de titular/documento é o CSV do gerador até lá.
- **Roteiros da P1.25**: esta entrega não reescreve os 81 itens da VISAN
  nem a lista do SAMAE — eles são da P1.25, versionados no repo e
  conferidos por `scripts/check-matriz-viva.py`. O avaliador **recusa**
  pontuar como ensaio um arquivo VISAN que não some 162.
- **Feriados**: nenhum é embutido no cálculo de dias úteis; o calendário
  do ensaio real entra por arquivo (`--feriados`).
- **Ensaio real e recriação real**: dependem de execução no PC do dono;
  nenhum tempo, pontuação ou contagem de banco é afirmado aqui.

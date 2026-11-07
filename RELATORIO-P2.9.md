# P2.9 — Relatório de entrega (carga com 300 mil ligações)

Frente de implementação, 07/11/2026. Base: HEAD conhecido `0fbcd22` de
`mastersonmdaniel/Ziip_San` (pós-pushes da Onda 1) + espelhos locais de
`~/workspace/ziipsan-producao/` (p2.5, ed-57, p4.7, ed-04) e fontes de
`~/workspace/ziipsan-retomada/fontes/`. **Nenhum push foi feito.** A carga
**não foi executada** (esta VM não tem JDK/Maven/Docker/PostgreSQL): a
entrega é o aparato completo e o registro-template; os números nascem da
execução no PC do dono, junto da CI. Nada foi inventado.

## O que a investigação encontrou (e o que NÃO encontrou)

- **Não há harness de carga pré-existente para reaproveitar.** Em todos
  os espelhos locais e no trecho do repo alcançado via MCP antes do
  **HTTP 429** (que interrompeu o espelho, conforme instruído paramos e
  seguimos com os espelhos locais) não existe script de carga, fixture de
  volume, teste de volume nem artefato da P0.23. O "teste de volume"
  citado nas rodadas da P2a foi, pelo diário e pelo `ci.sh` verificados,
  a própria suíte de integração sobre o banco descartável — não um
  harness de carga reutilizável. A P2.9 portanto **constrói** o harness,
  seguindo o método que o aceite da **P0.23** já definiu: ferramenta
  k6/Gatling, regressão de p95 > 15% falha (RNF-70), cenário Keycloak com
  kit de benchmark, comparação com a capacidade registrada (PLT-28).
- **Capacidade da P0.23 (PLT-28): número NÃO encontrado.** Nenhum espelho
  contém o registro da execução da P0.23 (pico do portal, p95 de login,
  dimensionamento da ARQUITETURA-ALVO §6). Tratamento: `carga-p0.23.env`
  é o ponto único de transcrição desses números e `checar-keycloak.sh`
  **falha fechado** sem eles. Pendência declarada abaixo.
- **Teto numérico do p95: NÃO consta dos espelhos** (o RNF é citado, o
  valor não). A sonda mede sempre e compara com (a) `P95_MAX_MS`
  configurável e (b) `P95_BASELINE_MS` da P0.23 com a regra de +15% do
  RNF-70; sem nenhum dos dois, o veredito é `MEDIDO_SEM_TETO_DEFINIDO`.
- **DDL verificada e usada**: V4_1 (billing: tarifas/faixas/contas),
  V4_3 da P2.5 (grupos, referências, cronograma), V5_1 (metering:
  hidrômetros, instalações, leituras, consumos), fixture
  `FictitiousTariffs` (valores fictícios declarados do produto, usados
  como tarifa de carga) e `RegistryCsvSchema` da P1.3 — **consultada**
  apenas para os campos obrigatórios da unidade; o CSV da carga
  (`ligacoes.csv`) tem colunas próprias e **não** é importado por esse
  schema. API local na porta 8090 e realm `ziipsan` conferidos no
  `docker-compose.yml`.
- **DDL NÃO verificada**: a V3_1 do registry (`consumer_units`,
  `categories`, ligação unidade↔categoria/economias) não está em nenhum
  espelho local. O seeder do registry isola esse desconhecimento num
  trecho marcado `[CONFERIR-V3_1]` e o orquestrador **para** se a
  contagem de unidades não bater — em vez de chutar colunas.

## Pacote (18 arquivos em `scripts/carga/` + `docs/carga/`, caminhos do repo em `entrega/`)

- `scripts/carga/` — `README.md` (roteiro de execução), `carga.env.example`,
  `carga-p0.23.env.example`, `gerar-massa.py`, `medir-api.py`,
  `api-probe.js` (k6 opcional), `coletar-recursos.sh`, `checar-keycloak.sh`,
  `executar-carga.sh`, `testes/test_carga.py` e `sql/` com
  `00-preflight.sql`, `10-seed-registry.sql`, `20-seed-tarifas-ciclo.sql`,
  `30-seed-medicao.sql`, `90-verificacao.sql`, `99-limpeza.sql`.
- `docs/carga/` — `REGISTRO-CARGA-P2.9.template.md` (o registro exigido:
  volume, duração, p95, uso de recursos) e `resultado-carga.schema.json`.

## Acceptance, item a item

1. **300 mil ligações sintéticas** — gerador determinístico (semente fixa,
   fórmulas fechadas, UUIDs `md5(semente:tipo:seq)` idênticos no CSV e
   nos seeders SQL) + seeders via `generate_series` sobre DDL verificada;
   massa **declarada sintética**, sem dado real nem documento/nome.
   RES 80%/COM 20%, 100 rotas/grupos, leituras e consumo de todas as 300 mil.
2. **100 mil contas em < 2 h** — subconjunto faturável = seq ≤ 100.000,
   **materializado no banco** (`consumption_records.billable`, ver
   correções); o orquestrador dispara o faturamento com relógio
   parametrizado (`CARGA_LIMITE_SEG=7200`, padrão do aceite), espera
   ativa por conclusão (comando `BILLING_DONE_CMD` ou contagem de contas
   concluídas) e grava a duração real. **Só se prova na execução.**
3. **p95 da API medido EM PARALELO** — `medir-api.py` (stdlib, sem
   dependência nova no PC) roda em segundo plano durante toda a janela,
   amostra cada resposta em CSV e consolida p50/p95/p99/erros ao fechar a
   janela; `api-probe.js` é o equivalente k6. Teto: parâmetro (acima).
4. **Realm do portal dentro da capacidade da P0.23 (PLT-28)** — sonda de
   discovery do realm durante a carga, registrada como **MEDIÇÃO
   PARCIAL, sem veredito de aprovação** (correção pós-revisão: os limites
   da P0.23 são de login/vazão, grandezas que a sonda não mede). A perna
   só aprova com o cenário que os limites descrevem; sem o registro da
   P0.23, segue bloqueada (pendência 1).
5. **Resultado registrado com volume, duração, p95 e uso de recursos** —
   `resultado.json` (schema em `docs/carga/`) com volume das contagens
   **medidas no banco** e picos de recursos consolidados + template de
   registro + CSVs de recursos/API/Keycloak e manifesto anexáveis.

## O que foi provado AQUI (lógica, não a carga)

- `python3 -m unittest discover -s scripts/carga/testes`: **7 testes OK**
  (após as correções pós-revisão) — determinismo (mesma semente → mesmo
  SHA-256; semente diversa → hash diverso), mistura 80/20 e volumes em
  escala reduzida, unicidade e formato dos UUIDs, faixas das fórmulas,
  rota começando em ROTA-001 e percentil **nearest-rank** com esperados
  recalculados (ver seção de correções, achado 18).
- Fumaça da sonda contra um HTTP local descartável: 4.938 amostras em 3 s,
  p95 calculado e veredito por teto emitido (ambiente de brinquedo —
  prova a mecânica, não desempenho de nada). Fumaça pós-correção do
  encerramento por SIGTERM (o caminho do orquestrador): 2.681 amostras
  em 3 s, **todas** no CSV (flush por amostra) e resumo gravado pelo
  handler — nenhuma amostra perdida no kill.
- `bash -n` nos três scripts shell: sintaxe OK. SQL conferido coluna a
  coluna contra as DDLs V4_1/V4_3/V5_1 dos espelhos (não executado: sem
  PostgreSQL nesta VM).

## O que só se prova na execução no PC do dono

- Os seeders contra o banco real migrado (incl. o trecho `[CONFERIR-V3_1]`
  ajustado) e o tempo de semeadura das 300 mil linhas.
- O faturamento real das 100 mil contas e sua duração (< 2 h?).
- O p95 real da API sob a carga e o comportamento do Keycloak.
- A CI total da camada continua sendo o portão do projeto para o código;
  esta tarefa não adiciona código Java nem migration.

## Correções pós-revisão (07/11/2026, frente de correção)

A revisão cruzada aprovou o aparato COM CORREÇÕES. Aplicadas todas, sem
push e sem executar a carga (VM sem Docker/JDK, como na entrega):

**Altas**

1+2. **Consolidação do p95 (`executar-carga.sh`)** — a consolidação da
   janela sobrescrevia o veredito do teto pelo da baseline (o último
   critério vencia). Agora usa a mesma função/semântica de
   `medir-api.py`: coleta os critérios definidos (teto; baseline × 1,15),
   **REPROVADO de qualquer um prevalece**, APROVADO só se todos os
   definidos passarem, e grava `teto_ms`/`baseline_ms`/
   `limite_regressao_15pct_ms` no resumo consolidado.
3. **Veredito global** — `MEDIDO_SEM_TETO_DEFINIDO` saiu da composição de
   aprovação: o global só é `APROVADO` com `veredito_p95 == APROVADO`
   (e Keycloak `APROVADO` e limite cumprido). Sem teto nem baseline, a
   perna de p95 fica inconclusiva e o `resultado.json` sai
   `REPROVADO_OU_INCOMPLETO` — falha fechada.
4. **Ordem preflight × sementes** — a checagem de categorias saiu do
   `00-preflight.sql` (que agora confere tabelas no schema do tenant e
   referência livre) e virou a **etapa 2 do preflight no orquestrador,
   logo após o `10-seed-registry.sql`** (junto da contagem de unidades):
   a primeira execução em tenant descartável novo não aborta mais por
   construção.
5. **Referência/ciclo × `CycleService.openReference`** — o
   `20-seed-tarifas-ciclo.sql` **não insere mais `billing_references`**
   por SQL (nem `cycle_activities`/`cycle_schedule_items`, que ele nunca
   inseriu): semear a referência bloquearia o `openReference` (conflito
   "já existe referência") e deixaria um ciclo sem cronograma — sem
   instância da atividade BILLING para comandar. O seeder fica com
   tarifa + grupos/rotas; a **abertura da referência, com o catálogo de
   atividades e os itens de cronograma, é da etapa de disparo via
   CycleService** (`BILLING_TRIGGER_CMD`/fiação M7 deve chamar
   `openReference` antes de comandar o BILLING), como o cabeçalho do
   arquivo e o README agora documentam. Nada permanece em SQL nesse
   ponto; a única parte do ciclo que continua em SQL é tarifa/grupos,
   que são dados de entrada do serviço, não estado do ciclo.
6. **Subconjunto faturável materializado** — `30-seed-medicao.sql` grava
   `billable = (seq <= faturar)`: TRUE só nas 100 mil do subconjunto,
   FALSE nas demais 200 mil (antes, todas nasciam TRUE e as 100 mil só
   existiam na coluna do CSV). Vínculo unidade↔grupo/rota: os grupos
   `CARGA-001..100` carregam `route_code = ROTA-001..100` (V4_3, o que o
   despacho do CycleService entrega ao lote) e a rota de cada unidade
   segue a mesma fórmula do CSV, documentada no trecho de unidades do
   `10-seed-registry.sql`. **Declaração explícita do limite:** o nome da
   coluna/tabela que guarda a rota **na unidade** mora na V3_1, não
   verificada — esse preenchimento continua no trecho `[CONFERIR-V3_1]`
   do passo manual já declarado (pendência 3); todo o resto do vínculo
   (grupo/rota + `billable`) está materializado em DDL verificada.

**Médias/baixas**

7. **Perna Keycloak** — `checar-keycloak.sh` não compara mais p95 de
   discovery com limite de p95 de login: a perna sai sempre
   **`MEDICAO_PARCIAL`, sem veredito de aprovação da PLT-28**, dizendo
   exatamente isso no `keycloak.json` (a consolidação do orquestrador
   produz o mesmo veredito). `KC_PORTAL_PEAK_RPS`, `KC_MAX_ERROR_RATE` e
   o p95 de login **não são conferidos por nenhuma peça** — nenhum texto
   do pacote afirma mais que "limites" são conferidos. Consequência
   assumida: com a perna parcial, o veredito global não aprova até
   existir o cenário que os limites descrevem.
8. **Perda de amostras no kill** — `medir-api.py` faz **flush por
   amostra** e trata SIGTERM com handler que sinaliza a parada e deixa o
   fluxo normal fechar o CSV e gravar o resumo (provado na fumaça
   acima). A consolidação ignora com guarda (`try/except` por linha) uma
   eventual última linha truncada, nas duas pernas, e o bloco roda com
   proteção contra `set -e`.
9. **Falha do disparo** — `BILLING_TRIGGER_CMD` roda com captura de rc:
   em erro, o orquestrador encerra as sondas, consolida, roda a
   verificação e grava `resultado.json` de falha com
   `disparo_faturamento.comando_rc` — sem sondas órfãs nem aborto
   silencioso.
10. **`resultado.json` estruturado** — `volume` vem das contagens
    **medidas** da `90-verificacao.sql` (`ligacoes_medidas`,
    `leituras_medidas`, `consumos_medidos`, `contas_concluidas`,
    `contas_erro`); do manifesto só entra `contas_alvo`. Recursos entram
    como `recursos.picos_por_container` (CPU/mem máximos por container,
    consolidados de `recursos.csv`) + nome do arquivo. Schema atualizado.
11. **k6 (`api-probe.js`)** — thresholds agora incluem **também** a
    regressão de +15% sobre `P95_BASELINE_MS` (RNF-70), além do teto;
    os dois valem juntos, igual à sonda Python.
12. **Relatório** — corrigido o item da `RegistryCsvSchema` acima:
    consultada para os obrigatórios da unidade; o CSV da carga tem
    colunas próprias e não é importado por esse schema.
13. **Ponto único de transcrição** — `P95_BASELINE_MS` mudou de
    `carga.env.example` para `carga-p0.23.env.example` (é número da
    P0.23). `KC_PORTAL_USERS_BASE`: **escolha declarada — saiu do
    example**, porque nenhuma peça o lia (número transcrito sem uso);
    volta se o cenário completo da PLT-28 for implementado.
14. **Código morto** — removidas as duas atribuições duplicadas de
    `KC_RC` no orquestrador (o valor nem entrava no veredito; o registro
    decide pelo `keycloak.json`).
15. **Limpeza (`99-limpeza.sql`)** — `tariff_versions`,
    `tariff_category_rules` e `tariff_blocks` agora filtrados pela
    tarifa da carga (id `...0100` via subconsultas) e
    `cycle_schedule_items` pela referência da carga — antes apagavam
    pelo tenant inteiro.
16. **Rota do CSV** — fórmula corrigida para `((seq-1) % 100) + 1`
    (seq 1 = ROTA-001, alinhada aos grupos do seeder), extraída para
    `gerar.rota()` e coberta por teste novo.
17. **Contagem de arquivos** — corrigida neste relatório: são **18**
    arquivos em `scripts/carga/` (16, incl. `sql/` e `testes/`) +
    `docs/carga/` (2).
18. **Percentil — MUDANÇA DE DEFINIÇÃO (correção de régua, não
    afrouxamento)** — a sonda Python usava interpolação por
    `round(p/100 × (n−1))`; passou a **nearest-rank** (`ceil(p/100 × n)`),
    a régua do k6, em `medir-api.py`, na consolidação do orquestrador e
    no `checar-keycloak.sh`. Esperados dos testes **recalculados e
    provados** sob a nova régua: em 1..100, p50 = 50 (antes 51),
    p95 = 95, p99 = 99; amostra [10,20,30,40]: p50 = 20, p95 = 40.
    Nenhuma asserção foi afrouxada: os valores novos são os que a
    definição nearest-rank produz, conferidos à mão.
19. **Preflight** — `information_schema.tables` filtrado por
    `table_schema = current_schema()` (schema do tenant) e removido o
    `PERFORM 1` sem efeito.

**Suíte após as correções:** `python3 -m unittest discover -s
scripts/carga/testes` → **7 testes OK**; `bash -n` nos três scripts
shell OK; schema JSON válido. As pendências originais abaixo
**permanecem** como estavam: registro da P0.23 não localizado (1),
fiação M7 (2) e trecho `[CONFERIR-V3_1]` (3) — nenhuma correção acima
as disfarça.

## Pendências honestas

1. **Registro da P0.23 / PLT-28 não localizado** — sem os números
   (pico do portal, p95 de login, base de 1 milhão de usuários do realm),
   a perna do Keycloak não pode dar veredito. Procurar no PC do dono o
   artefato da execução da P0.23; se ela nunca foi executada, a P2.9 fica
   bloqueada nesse item até a P0.23 rodar — não contornamos inventando.
2. **Fiação M7 (integração p2b)** — o disparo real do ciclo
   (`CycleService` → lote da P2.1) ainda não está ligado no app
   (relatório da P2.5); `BILLING_TRIGGER_CMD` fica vazio até lá e o
   orquestrador para. Se o M7 pousar antes da carga, basta preencher.
3. **V3_1** — conferir `\d consumer_units/categories` no banco real e
   completar o trecho marcado do `10-seed-registry.sql` (é o único passo
   manual de schema; estimado em minutos, mas é passo real).
4. **Caminho de sondagem da API** — o padrão é `/actuator/health`; se o
   app não expõe actuator, apontar `API_PROBE_PATHS` para endpoints de
   leitura reais do portal/API na configuração da execução.
5. **Espelho via MCP interrompido por HTTP 429** (declarado no item de
   investigação): se no repo houver artefato da P0.23 fora dos espelhos
   locais, a pendência 1 se resolve por transcrição, sem mudar o aparato.

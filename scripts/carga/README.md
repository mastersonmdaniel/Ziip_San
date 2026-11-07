# P2.9 — Carga com 300 mil ligações

Aparato executável da carga do aceite da P2.9 (plano v3.0): *carga com
300 mil ligações sintéticas: 100 mil contas faturadas em menos de 2 h sem
estourar o p95 da API medido em paralelo; durante a carga, o realm do
portal fica dentro da capacidade do Keycloak medida na P0.23 (PLT-28);
resultado registrado com volume, duração, p95 e uso de recursos*.

**A carga NÃO roda nesta entrega**: ela roda no PC do dono, junto da CI,
sobre o ambiente local de homologação (P0.27) com um tenant descartável.
Nenhum número de resultado existe ainda — o registro nasce da execução
(ver `docs/carga/REGISTRO-CARGA-P2.9.template.md`).

## Como rodar (PC do dono, Git Bash)

1. Ambiente no ar: `scripts/local-env.sh homolog` (API em 127.0.0.1:8090,
   Keycloak, PgBouncer) e um tenant **descartável** provisionado.
2. `cp scripts/carga/carga.env.example scripts/carga/carga.env` e preencha
   (tenant, referência, `BILLING_TRIGGER_CMD` — ver a nota M7 no exemplo).
3. `cp scripts/carga/carga-p0.23.env.example scripts/carga/carga-p0.23.env`
   e **transcreva a capacidade medida na P0.23** (PLT-28). Sem isso a
   checagem do Keycloak falha fechado — de propósito.
4. Confira o passo de unidades do `sql/10-seed-registry.sql` contra a
   V3_1 (`\d consumer_units`) — é o único trecho que depende de DDL que
   não estava nos espelhos (ver o relatório da entrega).
5. `bash scripts/carga/executar-carga.sh` — gera a massa, semeia, dispara
   o faturamento com a sonda de p95, a coleta de recursos e a sonda do
   Keycloak **em paralelo**, verifica e escreve `resultado.json`.
6. Preencha o registro em `docs/carga/` a partir do `resultado.json` e
   anexe os CSVs (`recursos.csv`, `api/amostras-api.csv`,
   `keycloak-amostras.csv`, `massa/manifesto.json`).

## Peças

| Arquivo | O que faz |
|---|---|
| `gerar-massa.py` | Massa sintética determinística (CSV + manifesto SHA-256); mesma fórmula/UUIDs dos seeders SQL |
| `sql/00-preflight.sql` | Falha cedo: tabelas presentes no schema do tenant, referência sem contas (categorias são conferidas DEPOIS do seed do registry, no orquestrador) |
| `sql/10-seed-registry.sql` | Categorias RES/COM (ids da fixture do produto) + unidades com a rota da massa — trecho `[CONFERIR-V3_1]` marcado |
| `sql/20-seed-tarifas-ciclo.sql` | Tarifa fictícia da fixture `FictitiousTariffs` (declarada fictícia no produto) + 100 grupos/rotas (V4_1/V4_3). A referência NÃO é semeada: quem abre é o `CycleService.openReference`, na etapa de disparo |
| `sql/30-seed-medicao.sql` | Hidrômetros, instalações, leituras e consumo das 300 mil (V5_1), via `generate_series`; `billable` materializado: TRUE só nas 100 mil faturáveis |
| `medir-api.py` | Sonda de p95 **em paralelo** (só stdlib, percentil nearest-rank); teto `P95_MAX_MS` e/ou regressão de 15% sobre `P95_BASELINE_MS` (RNF-70) — reprova em qualquer critério; sem nenhum, mede sem aprovar |
| `api-probe.js` | Equivalente k6 opcional, com os mesmos dois thresholds (teto e regressão +15%) |
| `coletar-recursos.sh` | `docker stats` amostrado durante a janela → `recursos.csv` (picos consolidados no `resultado.json`) |
| `checar-keycloak.sh` | Sonda de discovery do realm do portal sob a carga → `MEDICAO_PARCIAL`: não aprova a PLT-28 (os limites da P0.23 são de login/vazão, grandezas que ela não mede) |
| `executar-carga.sh` | Orquestra tudo, com relógio do aceite (7.200 s) e consolidação dos resumos |
| `sql/90-verificacao.sql` | Contagens finais (unidades, leituras, consumos, contas concluídas/erros, soma) |
| `sql/99-limpeza.sql` | Remove a massa do tenant da carga (confirmação explícita) |
| `testes/test_carga.py` | Testes da lógica (determinismo, fórmulas, percentis) — não são a carga |

## Decisões e limites (resumo; o relatório da entrega detalha)

- **Sem harness anterior para reaproveitar**: nos espelhos e no trecho do
  repo alcançado antes do HTTP 429 não há script de carga, fixture de
  volume nem resultado da P0.23. A P2.9 constrói o harness seguindo o
  método que a P0.23 definiu (k6/Gatling, RNF-70, kit do Keycloak).
- **p95 sem teto numérico inventado**: o valor do RNF não consta dos
  espelhos; a sonda compara com o parâmetro configurável e com a linha de
  base da P0.23 (+15%). Sem nenhum dos dois, o veredito da perna é
  `MEDIDO_SEM_TETO_DEFINIDO` e o resultado global NÃO aprova (falha
  fechada). A perna do Keycloak sai `MEDICAO_PARCIAL` pelo mesmo motivo:
  sonda de discovery não prova capacidade de login da PLT-28.
- **Disparo do faturamento é parâmetro** (`BILLING_TRIGGER_CMD`): a fiação
  do `CycleService` no app é o pacote M7 da integração p2b (relatório da
  P2.5); sem ela não existe comando real e o orquestrador para.

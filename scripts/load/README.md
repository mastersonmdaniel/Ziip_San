# Carga inicial (P0.23)

Cenários, escalas e o que foi medido de verdade. Nenhum número desta página é
estimativa: o que não foi medido está marcado como pendente.

## Cenários

| Cenário | Script | Escala do plano | Escala executável nesta onda |
|---|---|---|---|
| API (leitura + cadastro) | `api-read.js` | Tenant sintético de 20 mil ligações | Reduzida (VUS/DURATION por variável); 20 mil ligações pendente |
| Keycloak, kit oficial | `keycloak-benchmark.sh` (roteiro) | Logins, refreshes, `client_credentials`; meta de pico do portal no vencimento e na emissão da conta | Não executado (sem Keycloak em produção nesta VM) |
| Keycloak, token endpoint | `keycloak-token.js` | — (substituto reduzido, não é o kit) | Executa contra Keycloak local quando disponível (variáveis KC_*) |
| Autenticador do portal (índice cego) | `BlindIndexBenchmarkTest` (módulo `ziipsan-security`) | Base sintética de 1 milhão de usuários no realm do portal | Reduzida: 100 mil documentos sintéticos, em memória, sem PostgreSQL |

## Medições realizadas nesta onda (escala reduzida, sem invenção)

**Índice cego** (`BlindIndexBenchmarkTest`, módulo `ziipsan-security`, 8 testes verdes
no módulo contando o `BlindIndexTest`): 100 mil documentos sintéticos (CPFs
sequenciais normalizados), em memória, sem PostgreSQL, HMAC-SHA-256 + HashMap —
construção em 6.610 ms (~15.129 índices/s); lookup em 10 mil consultas: p50 = 2 µs,
p95 = 4 µs, p99 = 15 µs. Primeira execução (antes da correção de uma divisão inteira
no cálculo do throughput) deu 5.301 ms de construção — variação de carga da VM
compartilhada, não erro de medição; os percentis de lookup são os da segunda
execução. Isto caracteriza o custo do HMAC + lookup, não aprova a escala cheia:
1 milhão de usuários, no banco, no realm do portal, continua pendente (item 3 abaixo).

**API e Keycloak**: nenhum p95 medido nesta onda (sem ambiente com o porte do §6 e
sem Keycloak em produção nesta VM). Os scripts estão prontos; a baseline continua
`null` por decisão, não por esquecimento.

## Gate (RNF-70)

`check-regression.py` falha se o p95 regredir mais de 15% sobre a baseline de
`baseline.json`. A baseline ainda é `null`: nenhum p95 real foi medido nesta onda, e
a baseline só entra por revisão humana — nunca automaticamente a partir de uma
medição, e nunca a partir de estimativa. O primeiro p95 revisado (com escala, data e
commit registrados) vira a baseline.

## Comparação com o dimensionamento (ARQUITETURA-ALVO §6) e o RNF-17

| Componente | Dimensionamento §6 | Medido nesta onda |
|---|---|---|
| API 3 × 2 vCPU / 4 GB; worker 2 × 2 vCPU / 4 GB | — | Não medido (sem ambiente com esse porte nesta VM) |
| Keycloak 2 × 1 vCPU / 2 GB | — | Não medido; RNF-17 (2+ réplicas, `start --optimized`, queda sem perda de sessão) é ensaio da P0.29/LC.6, e o compose local usa `start-dev` |
| PostgreSQL 4 vCPU / 16 GB / 500 GB SSD + standby + réplica | — | Não medido |

## Pendências (com o harness pronto)

1. Escala cheia da API (20 mil ligações) e carga de referência completa (P2.9: 300 mil
   ligações, 300 schemas) — exigem ambiente com o porte do §6, fora desta VM.
2. Kit oficial do Keycloak — exige Keycloak em produção (P0.29) e dataset do portal.
3. Base de 1 milhão de usuários no realm do portal — exige o realm do portal carregado
   e o banco com a base sintética.
4. Meta numérica de pico do portal no vencimento e na emissão da conta — o plano pede
   a meta registrada, mas não a quantifica: pendência do dono/da P0.29 antes de qualquer
   comparação de "dentro/fora da meta".
5. Baseline de p95 (RNF-70) — depende do item 1.

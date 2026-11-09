# ED-01 — Massa do tenant de demonstração: estado e procedimento de atualização a cada versão

**Sessão produtora (Produtor):** `002855fb-1e26-4fcb-86e7-4b568c15a6eb`
**Data:** 09/11/2026

## 1. O que o aceite pede

Aceite da ED-01 (fonte única, registro ED-01 em
`tarefas-onda-B.json`): "massa do tenant de demonstração atualizada a
cada versão". O tenant é o do ambiente de demonstração da **P3.23**
(perfil `demo` da P0.27, separado da homologação; piso do aceite da
P3.23: **≥ 10.000 unidades fictícias**, sem dado pessoal real,
documentos marcados como sintéticos).

## 2. Qual é a massa atual (fontes locais, sem afirmação além delas)

Duas massas locais se relacionam com o tenant de demonstração — este
documento **não** as funde nem inventa uma terceira:

| Massa | Pacote local | Forma | Números com fonte |
|---|---|---|---|
| Massa sintética da P3.23 | `ziipsan-producao/p3.23/` (`scripts/demo/gerar-massa-demo.py`) | CSV canônico gerado por script determinístico; seed no banco é esqueleto parcial (`10-seed-demo.sql`, trecho `[CONFERIR-V3_1]` declarado no pacote) | Padrão 12.000 unidades (piso recusado < 10.000), 80% RES / 20% COM, 100% das linhas marcadas `documento_sintetico=SIM`/`marca=SINTETICO` (teste do gerador reexecutado na revisão cruzada da P3.23) |
| **Base fictícia de 12.000 UCs** | `ziipsan-producao/base-ficticia-12k/` (decisão do dono nº 34, 09/11/2026) | Seed SQL fora de migration (`seed/base-ficticia/`), tenant fictício dedicado | 12.000 UCs exatos em **23 situações**; **123.120 linhas em 32 tabelas** (estado final com o complemento de perfil); dois municípios fictícios — Porto das Gaivotas 9.000 (75%) e Vale do Rio Claro 3.000 (25%); gerador determinístico, **semente `20261109`**, regenerações com hashes idênticos (309/309 no pacote original); provas em PostgreSQL 16 descartável **7/7** (original) e **12/12** (complemento), por produtor, revisor e confirmador; limpeza exata → 0 linhas do tenant fictício, sentinela intacta |

**Declaração deste produtor:** a massa de demonstração vigente é a
**base fictícia de 12.000 UCs** (é a massa completa, provada em banco e
atualizada por último, em 09/11/2026, já com o complemento de perfil
de dois municípios). A massa da P3.23 permanece como gerador do tenant
de demonstração do ensaio de POC (CSV canônico + guardas
`DemoTenantGuard`); onde as duas divergirem em cobertura, vale o
registro de cada pacote — **nenhuma atualização de massa foi executada
nesta sessão da ED-01** e nenhuma é afirmada aqui.

Pendências herdadas da massa (do próprio pacote, não resolvidas aqui):
**P-BF-06** (tarifa por município NÃO suportada pelo desenho atual),
**P-BF-07** (rotas por município PARCIAL) e **P-BF-08**
(relatórios/faturamento por município PARCIAL) — decisão de desenho do
dono, fora deste pacote; e o pouso do estado final da base na main
estava parcial (lotes) por parada HTTP 429 — estado da main não
verificado neste bloco.

## 3. Procedimento de atualização a cada versão (o que se regenera)

Executar a cada versão do produto que vá a ensaio/demonstração.
Procedimento montado **somente** a partir dos artefatos existentes:

1. **Regenerar a base fictícia** com o gerador do pacote
   (`gerar-base-ficticia.py`, Python 3 stdlib): semente vigente
   `20261109`. Se a versão mudar a matriz de situações ou o DDL de
   referência, a mudança entra **primeiro** na matriz
   (`MATRIZ-SITUACOES.md`/`matriz.csv`, soma obrigatória = 12.000) e
   no relatório de cobertura — nunca direto no SQL gerado.
2. **Conferir determinismo:** regenerar duas vezes e comparar os
   SHA-256 do `manifesto.json` (padrão provado: hashes idênticos).
3. **Provar carga e limpeza** com o harness do pacote
   (`prova-carga-limpeza.sh`, PostgreSQL descartável): placar
   esperado **12/12** no estado atual (7 asserções originais + 5 do
   complemento de dois municípios). Placar diferente = atualização
   **não** aceita; registrar o log em `harness-logs/`.
4. **Recriar o ambiente de demonstração** pelo aparato da P3.23
   (`scripts/demo/recriar-demo.sh` sobre o perfil `demo` da P0.27,
   tempo medido) e carregar a massa regenerada no tenant de
   demonstração (`00-tenant-ficticio.sql` primeiro, depois
   `carga-NNNN.sql` em ordem), respeitando as guardas do
   `DemoTenantGuard` (tenant/schema ≠ dev/homolog;
   `ziipsan.fiscal.production-allowed=false`).
5. **Regenerar também a massa CSV da P3.23** (`gerar-massa-demo.py`)
   quando o ensaio de POC for o destino da versão, e rodar os testes
   do gerador (`testes/test_demo.py`).
6. **Registrar a atualização:** versão do produto, data, semente,
   hash do manifesto, placar do harness e tempo de recriação — no
   registro do ensaio (`REGISTRO-ENSAIO-ED-01.template.md`) e no
   diário de bordo, pelo fluxo normal do projeto.
7. **Limpeza ao fim dos testes** (obrigatória, decisão 34):
   `limpeza-base-ficticia.sql` remove **só** o tenant fictício
   (`11111111-2222-4333-8444-555555555555`); conferir 0 linhas nas 32
   tabelas e nenhum outro tenant tocado.

## 4. O que este procedimento NÃO é

- Não é uma atualização executada: os passos 1–7 são o procedimento;
  nesta sessão da ED-01 **nenhum passo foi rodado** (sem Docker/
  PostgreSQL/JDK nesta VM para os passos 3–4; declarado).
- Não autoriza carregar a massa em produção: o seed vive fora de
  `db/migration`, com aviso de fictício em cada arquivo, e a decisão
  34 proíbe a massa em migration versionada de produção.

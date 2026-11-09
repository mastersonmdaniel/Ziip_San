# ÍNDICE — Conjunto contratual pronto para assinatura (LC.9)

> **Produtor:** sessão `d9b12bda-ac3a-4b63-b86d-a806e37d4f49` (papel: Produtor).
> **Tarefa:** LC.9 — "Contrato SaaS, DPA e acordo de tratamento assinados
> com o piloto" (tamanho P; deps P3.9, LC.8; origem ADR-0002, ADR-0019,
> ADR-0020, ADR-0023, ADR-0025).
> **Estado deste conjunto: PRONTO PARA ASSINATURA — NÃO ASSINADO.**
> Nenhuma assinatura foi colhida ou simulada. Nenhuma cláusula nova foi
> redigida neste pacote: as minutas da P3.9 são **montadas por referência**
> (§1), e os elementos do aceite da LC.9 entram por **ficha de incorporação
> por referência** (`FICHA-INCORPORACAO-LC9.md`), nunca por reescrita.

## 0. Modelo vigente — licitação, não piloto (DECISOES-DO-DONO, item 34)

O aceite da LC.9 fala em "assinados com o piloto". Pelo **item 34** das
decisões do dono (09/11/2026, 00:19 −03), **não há piloto nem cliente
real**: "O PRODUTO SERÁ VENDIDO EM LICITAÇÃO APÓS A CONCLUSÃO, AINDA NÃO
TEM CLIENTE." Consequência declarada para todo este pacote:

- quem assina é o **cliente futuro** (o ente vencedor da contratação por
  licitação), no papel de **controlador**; a ZiipSan assina como
  **operadora**;
- a "aprovação das minutas pelo piloto" da P3.9 lê-se, neste modelo, como
  **aprovação pelo cliente futuro na contratação** — ato que não existe
  ainda e **não** é presumido em nenhum documento deste conjunto;
- o termo de assinatura (`TERMO-ASSINATURA-LC9.md`) fica **em branco**.

## 1. Peças do conjunto (minutas da P3.9, por referência)

Origem de todas: `~/workspace/ziipsan-producao/P3.9/entrega/docs/contratos/`
(destino no repo: `docs/contratos/`, a mesma pasta lógica do arcabouço da
ED-05). Estado herdado da P3.9: **PRODUZIDAS COM APROVAÇÃO PENDENTE**,
revisão cruzada APROVADO COM RESSALVAS (B1–B4, todas baixas) e confirmação
pós-correção registradas em `P3.9/REVISAO-P3.9.md` e
`P3.9/REVISAO-CONFIRMACAO-P3.9.md`. **Este pacote não altera uma linha
das minutas.**

| # | Peça | Papel no aceite da LC.9 | Como integra o ato de assinatura |
|---|---|---|---|
| 1 | `MINUTA-CONTRATO-SAAS.md` | **Contrato SaaS** | Instrumento principal — assinado |
| 2 | `MINUTA-DPA.md` — título oficial: "Acordo de Tratamento de Dados Pessoais (DPA)" | **DPA** e **acordo de tratamento** (são o mesmo instrumento — ver divergência D-LC9-02 no RELATORIO-LC.9) | Parte integrante do Contrato (Cl. 8ª) — assinado |
| 3 | `MINUTA-SLA-SUPORTE.md` | Política de SLA do nível de serviço (ADR-0025) | Anexo integrante por remissão (Contrato, Cl. 7ª) |
| 4 | `MINUTA-SUBOPERADORES-TRANSFERENCIA.md` | Lista de suboperadores e transferência (parte do DPA, Cl. 10ª) | Anexo integrante do DPA |
| 5 | `MINUTA-TERMOS-DE-USO.md` | Termos da plataforma (subordinados ao Contrato e ao DPA) | Anexo integrante |
| 6 | `MINUTA-TERMO-HOMOLOGACAO-LOCAL.md` | **NÃO integra este ato** — pela ressalva da própria minuta (correção B3 da P3.9), ele é assinado com o cliente na homologação local, não na LC.9 | Referenciado, fora do ato |
| — | `INDICE-E-RASTREABILIDADE-P3.9.md` | Rastreabilidade afirmação→fonte das minutas | Documento de referência do conjunto |

## 2. Peças novas deste pacote (LC.9)

| Arquivo | Conteúdo |
|---|---|
| `INDICE-CONJUNTO-CONTRATUAL-LC9.md` | Este índice: montagem por referência e modelo de licitação |
| `FICHA-INCORPORACAO-LC9.md` | Incorporação por referência dos 5 elementos do aceite (papéis; atestado ADR-0023; nível de serviço ADR-0025; risco residual LC.3; telemetria P4.8) com o estado de cada um nas minutas |
| `CHECKLIST-ASSINATURA-LC9.md` | Pré-condições do aceite como portão de assinatura (todas **não verificadas** nesta data) |
| `TERMO-ASSINATURA-LC9.md` | Termo de assinatura — **em branco** |
| `PENDENCIAS-LC9.md` | Os 14 valores abertos herdados da P3.9 + pendências próprias da LC.9, nomeadas |

## 3. Aceite da LC.9 (fonte única, íntegra)

Fonte: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
registro `LC.9`:

> "contrato SaaS, DPA e acordo de tratamento, a partir das minutas
> aprovadas na P3.9, assinados com o piloto antes do primeiro dado real e
> depois do pentest da LC.8 sem achado crítico ou alto aberto; Ziip SAN
> como operador e piloto como controlador; cláusula de emissão do
> atestado com prazo contado do corte (ADR-0023); nível de serviço
> escolhido (padrão ou Setor Público, ADR-0025); contrato declara o risco
> residual de desastre regional (ver LC.3); DPA registra a telemetria de
> produto agregada (ver P4.8)"

Duas expressões do aceite **não se sustentam literalmente** e são
tratadas como divergência declarada, não como fato (detalhe no
`RELATORIO-LC.9.md`): (i) "minutas **aprovadas** na P3.9" — a P3.9 as
entregou com **aprovação pendente** (não há aprovador); (ii) "assinados
com o **piloto**" — não há piloto (item 34); lê-se "cliente futuro, por
licitação" (§0).

# ED-01 — Pacote de ensaio final: roteiros VISAN e SAMAE (só o que tem fonte)

**Sessão produtora (Produtor):** `002855fb-1e26-4fcb-86e7-4b568c15a6eb`
**Data:** 09/11/2026

> Regra deste documento: **nenhum ponto de roteiro foi inventado.**
> Item de roteiro sem fonte local está marcado **[SEM FONTE]** e vira
> pendência nomeada. Os números 162/146 e "nenhuma falha crítica" são
> **meta do plano** (aceite da ED-01), citados como meta — não são
> resultado de ensaio nenhum.

## 1. A meta final (fonte: aceite da ED-01; marco M12 do plano)

- Roteiro da **VISAN: pelo menos 146 de 162 pontos**;
- Roteiro do **SAMAE: nenhuma falha em item crítico**;
- Em **dois ensaios consecutivos**, feitos **no tempo de preparo que
  os editais concedem** (a P3.23 fixa os tempos de referência dos
  editais em **5 e 7 dias úteis** cronometrados);
- Marco **M12** (prontidão de POC).

Metas intermediárias do plano (contexto, mesma fonte — `PLANO-v3.0.md`,
seção "Ensaios de POC"): fim da onda A ≥ 120 de 162 na VISAN e nenhuma
falha no SAMAE nas seções 2.4 a 2.19, 2.28 e Item 3; ensaio do P3
(P3.23) ≥ 50 de 162 e SAMAE 2.4/2.5 sem falha.

## 2. Quem é dono dos roteiros (fonte: aceite da P1.25)

A **P1.25** ("Roteiros internos de POC, matriz viva e modelos
jurídicos", ADR-0023) é a dona: "roteiro da VISAN (**81 itens**, com a
forma de demonstração de cada um) e do SAMAE (**lista crítica com
subitens e itens sorteáveis**) **versionados no repositório**, cada
item ligado a requisito, ED, tarefa e fase". A **P3.23** é dona do
**ensaio e do registro** (executar, cronometrar, gravar resultado por
item na matriz viva) — e declara expressamente que **não** reescreve
item nenhum dos roteiros.

## 3. O que EXISTE nas fontes locais sobre os roteiros

### 3.1 Estrutura e especificação (com fonte)

| Fato | Fonte local |
|---|---|
| VISAN tem **81 itens / 162 pontos**, com forma de demonstração por item | Aceite da P1.25 (`tarefas-P1.json`); `p3.23/entrega/docs/demo/MATRIZ-VIVA-E-ENSAIO.md`; constante `VISAN_PONTOS_TOTAIS = 162` em `avaliar-ensaio.py` e `PocEnsaioCriterion.java` (P3.23) |
| VISAN organizada por **alíneas** (b a p e r na onda A; q, s e t no grupo B1) | `PLANO-v3.0.md`, seções da onda A e do grupo B1 |
| SAMAE organizado por **seções numeradas** (2.x) + **Item 0 / Item 3**; lista crítica com subitens e itens sorteáveis | Aceites das tarefas (abaixo) e aceite da P1.25 |
| SAMAE: itens ligados a códigos **SG** (ex.: SG-154 crítico, SG-782) | Aceites da ED-88 e da ED-39; CHECAGEM-MONTAGEM.md |
| Formato do resultado por item (item, roteiro, seção, pontos máx./obtidos, resultado, requisito, tarefa, teste, evidência, duração) | `p3.23/entrega/docs/demo/MATRIZ-VIVA-E-ENSAIO.md` + `resultados-ensaio.template.csv` |
| Régua executável do ensaio **do P3** (≥50/162; SAMAE 2.4/2.5 sem falha; VISAN que não soma 162 = `INCONCLUSIVO_ESTRUTURA`; não-executado ≠ "sem falha") | `p3.23/entrega/scripts/demo/avaliar-ensaio.py` + `PocEnsaioCriterion.java` + testes dos dois lados (revisão cruzada da P3.23: casos de fronteira conferidos) |

### 3.2 Fragmentos citados nos aceites das tarefas do recorte (com fonte)

São **citações** dos aceites — provam que o item existe no roteiro e a
qual tarefa se liga; **não** reproduzem o texto nem a pontuação do
item no roteiro oficial.

**VISAN — códigos POC citados:** ED-06 → POC-04, POC-05, POC-07
(alínea b); ED-08 → POC-01, POC-03; ED-12 → POC-08, POC-11; ED-13 →
POC-10 (alínea c); ED-14 → POC-12, POC-14; ED-18 → POC-15, POC-17;
ED-19 → POC-15, POC-16, POC-21; ED-22 → POC-51; ED-23 → POC-26;
ED-27 → POC-29; ED-29 → POC-32, POC-33; ED-31 → POC-34, POC-36;
ED-32 → POC-37, POC-40, POC-66, POC-68; ED-34 → POC-31, POC-41;
ED-35 → POC-47, POC-48; ED-36 → POC-42, POC-46; ED-38 → POC-69;
ED-41 → POC-20; ED-46 → POC-56, POC-68; ED-20 → POC-52, POC-54;
ED-39 → POC-55; ED-42 → POC-70, POC-75; ED-43 → POC-71, POC-74,
POC-76; ED-49 → POC-77, POC-81.

**SAMAE — itens/seções citados:** 2.5 (ED-05, ED-07); Item 3 —
manutenção e suporte (ED-05); 2.7 (ED-06); 2.8 (ED-12, ED-13); 2.9
(ED-14); 2.10 (ED-15); 2.11 (ED-18, ED-19); 2.13 (ED-22, ED-24,
ED-25); 2.14 (ED-26, ED-27); 2.15 (ED-29, ED-30, ED-31); 2.16.18
(ED-32); 2.17 (ED-34, ED-36, ED-37, ED-58); 2.19 (ED-35, ED-41);
2.28 (ED-46); 2.22, 2.24, 2.26, 2.27 e Item 0 (grupo B1, plano);
2.24 (ED-39); SG-154 — fossa séptica, **item crítico na POC**
(ED-88, via CHECAGEM-MONTAGEM.md).

### 3.3 Artefatos de ensaio já produzidos por outros pacotes (com fonte)

| Artefato | O que é | Limite declarado na fonte |
|---|---|---|
| `ed-39/entrega/docs/cliente/POC-55-E-SAMAE-2.24-ROTEIRO.md` | Roteiro executável do ensaio do app do cliente (9 passos por plataforma) + seção SAMAE 2.24 | Estado **PENDENTE EXTERNO**: exige aparelhos reais, contas de loja do prestador e o roteiro oficial do edital, "que não está nesta VM" |
| `ed-30/.../Samae215NegCoverage.java` + relatório da ED-30 | Mapa de **11 exigências** da fatia de negativação do item **2.15** do SAMAE, ligadas às classes reais | "Códigos SG individuais da fatia **não confirmados** nas fontes — nenhum SG inventado; atribuição pendente do roteiro SAMAE" (marcador honesto idêntico na ED-31: 10 exigências mapeadas, zero códigos SG na entrega) |
| Templates da P3.23 (`REGISTRO-ENSAIO-POC`, `RELATORIO-COBERTURA-POC`, CSVs, schema JSON) | Forma do registro por item e do relatório de cobertura (requisito, tarefa, teste, item) | São **templates**: sem ensaio executado, todos os campos ficam PENDENTE por desenho |

## 4. O que NÃO existe nas fontes locais — [SEM FONTE]

| Ausência | Evidência da ausência | Pendência |
|---|---|---|
| **Os 81 itens da VISAN** (texto, forma de demonstração e pontos por item) | `RELATORIO-P3.23.md`: "Os itens dos roteiros **não estão nos espelhos/fontes locais — só a especificação deles**. Por isso nenhum item de roteiro foi escrito aqui" | **P-ED01-03** — obter os roteiros versionados da P1.25 (repositório/levantamentos 18d–18h) antes de qualquer ensaio; sem eles o ensaio da ED-01 é **inexequível**, não apenas não executado |
| **A lista crítica do SAMAE item a item** (quais itens são críticos, subitens, sorteáveis, códigos SG completos) | Mesmo registro da P3.23; ED-30/ED-31 declaram SG não confirmados; ED-13 declara o item 2.8 "NÃO VERIFICÁVEL" sem o roteiro | **P-ED01-03** (mesma) |
| Pontuação por item da VISAN (quanto vale cada POC-xx) | Nenhuma fonte local traz pontos por item; só o total 162 e a estrutura 81 itens | **P-ED01-03** (mesma) |
| Régua executável da **meta da ED-01** (≥146/162; SAMAE crítico inteiro) | Revisão cruzada da P3.23: "**A meta da ED-01 (146/162) não é usada em nenhum veredito**" do avaliador existente (a régua dele é a do P3: 50/162 e SAMAE 2.4/2.5) | **P-ED01-07** — na execução do ensaio, estender a régua (avaliador + `PocEnsaioCriterion`) para 146/162 e para a lista crítica completa, com testes de fronteira novos (145 reprova / 146 aprova); **não** feito neste pacote para não simular critério sem a lista crítica (P-ED01-03) |

## 5. Como o ensaio da ED-01 se executa (quando as pendências caírem)

Pré-condições cumulativas: roteiros oficiais da P1.25 disponíveis
(P-ED01-03); ambiente de demonstração da P3.23 recriado e com a massa
atualizada (ver `MASSA-DEMONSTRACAO-ATUALIZACAO.md`); kit físico
conferido (`KIT-DE-CAMPO-CHECKLIST.md`, P-ED01-01); régua estendida
(P-ED01-07). Execução: `executar-ensaio.sh` da P3.23 (cronômetro
contra 5/7 dias úteis), resultado **por item** no CSV da matriz viva,
cobertura (requisito, tarefa, teste) sem lacuna, registro em
`REGISTRO-ENSAIO-ED-01.template.md` — **duas vezes consecutivas**,
as duas com a meta atingida, para o marco M12 se declarar.

**Estado nesta entrega: nenhum ensaio foi executado nem simulado.**

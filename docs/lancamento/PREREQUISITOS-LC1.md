# LC.1 — Pré-requisitos com estado conferido localmente

- **Pacote:** LC.1 — Ensaio de implantação na AWS com dado sintético
- **Produtor:** sessão `a1981a33-6f88-49c6-8f6f-f9d70266f27b` (papel: Produtor)
- **Data da conferência:** 09/11/2026 (bloco de conclusão).
- **Método:** só fontes locais (`~/workspace/ziipsan-producao/`,
  `~/workspace/ziipsan-retomada/fontes/`, mapa de coordenação do bloco e
  memória consolidada do projeto). **GitHub em parada dura (HTTP 429)**:
  nenhum estado de pouso foi reverificado no repositório neste pacote —
  limite declarado em cada linha onde ele importa.
- **Leitura honesta:** "produzido localmente" ≠ "aceite concluído". Onde a
  fonte não existe localmente, consta **[SEM FONTE]** nomeado; onde o valor
  falta, **[A DEFINIR]**.

## 1. GL.4 — Aceite da homologação local (dep direta da LC.1) — NÃO CONCLUÍDO

- **Aceite da GL.4 (fonte):** decisão formal do dono do produto e do piloto
  sobre GL.2 e GL.3 (+ conferência FEBRABAN 150 da P3.24 quando aplicável).
- **Estado conferido:** o dossiê da GL.4 está **em produção neste bloco**
  (frente "P3.13 + GL.4", produtor `ca3d8c13-25d9-449d-828d-89c54bbff8fd`,
  conforme `conclusao/MAPA-COORDENACAO.md`); à data desta conferência **não
  existe pasta `gl.4/`** em `ziipsan-producao/` (verificado por listagem).
- **E a decisão?** A decisão formal é **do dono** (e, no texto original, do
  piloto — que não existe: decisão do dono nº 34 substituiu o piloto por
  base fictícia e venda por licitação pós-conclusão). Mapa do bloco,
  classe da GL.4: "A parcial + C" — dossiê produzível, **decisão pendente
  do dono**. Logo: **A2 não cumprido** à data deste pacote. O roteiro trata
  isto no passo 0.2 (sem decisão formal, o ensaio não começa) — pendência
  **P-LC1-07** (decisão formal da GL.4, do dono) alimentada por P-LC1-01
  apenas em sequência: primeiro a decisão, depois a conta.
- **Base da GL.4 que já existe localmente:** pastas `gl.1/`, `gl.2/`,
  `gl.3/` com relatórios e entregas (GL.1 com revisão "APROVADO COM
  RESSALVAS" registada no seu relatório) — evidência de produção local;
  o placar/aceite formal dessas frentes não é presumido por este pacote.

## 2. ED-01 — Prontidão de POC (dep direta da LC.1) — NÃO CONCLUÍDO

- **Aceite da ED-01 (fonte, `tarefas-onda-B.json`):** PD-01 — onda A, grupo
  B1 da onda B e tarefas do P4 concluídos (B2 **fora** da prontidão);
  kit de campo mantido; massa de demonstração atualizada; meta final de
  ensaios (VISAN ≥146/162 e SAMAE sem falha crítica, em dois ensaios
  consecutivos — marco M12).
- **Estado conferido:** a verificação de prontidão está **em produção neste
  bloco** (produtor `002855fb-1e26-4fcb-86e7-4b568c15a6eb`, mapa do bloco).
  A pasta `ed-01/` **existe mas está vazia** à data desta conferência
  (subpastas `entrega/` e `trabalho/` sem nenhum ficheiro — verificado por
  listagem recursiva): **não há material da ED-01 para cruzar** e nenhum
  resultado é presumido. **A3 não cumprido** à data deste pacote.
- **Nota de recorte (A4):** mesmo quando a ED-01 concluir, ela **não** cobre
  o grupo B2 (PD-01 expressa). O B2 é exigência própria da LC.1 (passo 0.1
  do roteiro), a conferir pela evidência dos pacotes do B2 — ver §3.

## 3. Critério de conclusão das fases/ondas (A1) — NÃO CONCLUÍDO à data; limite declarado

- **O que a ED-01 cobre:** PD-01 (onda A + B1 + P4) — quando concluir,
  serve de evidência para essa fatia do passo 0.1.
- **O que ela não cobre:** B2 (ED-56, ED-69 a ED-79, ED-83 a ED-94, ED-96 a
  ED-99, ED-105 — lista da fonte da ED-01) e as fases P0–P3/GL em si.
- **Evidência local disponível hoje:** estado consolidado do projeto
  (memória do dono/projeto, 09/11/2026): **262/286 tarefas executadas e na
  main (91,6%)** antes deste bloco — P0–P2 100%, P3 40/41, P4 5/6, GL 3/4,
  ED 92/98 — e este bloco de conclusão está a produzir os 18 pacotes
  restantes (P3.13, GL.4, ED-01, ED-51, LC.1–LC.14). As pastas de pacotes
  em `ziipsan-producao/` (incl. todo o B2: `ed-69/`…`ed-99/`, `ed-105/`,
  com relatórios/revisões/confirmações) são a evidência local de produção.
- **Limite:** sem acesso ao GitHub (429), este pacote **não reverifica**
  pousos nem o HEAD; e produção local em curso ≠ conclusão formal. Logo:
  **A1 consta NÃO cumprido à data deste pacote**, e o passo 0.1 do roteiro
  é a conferência formal que o ensaio exigirá, já com o bloco concluído e
  os pousos reverificados pós-429 (ver LANDING.md).
- **Exclusões confirmadas na fonte:** P4.6, ED-68, ED-102, ED-103 e ED-104
  (seção de pendentes/sob demanda) fora do critério; LC.15 fora do caminho.

## 4. Interfaces da P0.45 — EXISTEM como desenho e implementação local; suíte não localizada como artefacto único [parcial]

- **Aceite da P0.45 (fonte, `tarefas-P0.json`):** interfaces de
  armazenamento de objetos, chaves (`TenantKeyProvider`), segredos, fila e
  envio de e-mail; implementação local (emulador de S3, cofre local,
  e-mail com gravação sem envio externo); **suíte de contrato por interface
  no CI contra a implementação local, pronta para rodar contra a AWS na
  LC.1**; teste de fronteira (sem SDK de provedor em domínio) e teste de
  varredura (sem dado pessoal real sem marca de sintético).
- **Mapeamento local → AWS (ADR-0033, item 3):** objetos → S3 com Object
  Lock; chaves → AWS KMS; segredos → AWS Secrets Manager; fila → a mesma
  outbox no RDS (fora do recorte A7 da LC.1); e-mail → serviço definido na
  IaC (Amazon SES ou equivalente — **[A DEFINIR]**, P-LC1-02).
- **Artefactos locais localizados:** documento do ambiente local que
  descreve a operação das interfaces locais —
  `p2.18/espelho/referencias/local-env-P0.45.md` e espelhos de
  `docs/operacao/local-env.md` (ex.: `p4.7/espelho/`) — e os ADR-0033 /
  ADR-0011 (este em `p4.7/espelho/docs/arquitetura/adr/`; ADR-0033 em
  `P3.9/fontes-repo/`).
- **[SEM FONTE] nomeado:** **não existe pasta de pacote `p0.45/`** em
  `ziipsan-producao/` nem artefacto local único da suíte de contrato
  (código/placar) localizável nesta conferência — a suíte vive no
  repositório (CI), inacessível neste bloco (429). O passo 0.7 do roteiro
  exige a sua localização e o placar local **antes** do ensaio
  (pendência **P-LC1-08**: localizar a suíte da P0.45 no repositório e
  arquivar o placar contra o alvo local como pré-evidência).

## 5. Linha de base da P0.27 — ambiente documentado localmente; placar numérico [SEM FONTE]

- **Aceite da P0.27 (fonte):** Docker Compose com PostgreSQL 17, Keycloak,
  emulador de S3 e cofre local; perfis dev/homologação local/demonstração
  local recriáveis do zero por script, com tempo medido; mesma imagem da
  P0.5 e job `migrate`; nenhum recurso na AWS.
- **Artefacto local localizado:** `docs/operacao/local-env.md` (espelhos em
  `p4.7/espelho/`, referência em `p2.18/espelho/referencias/`) descreve os
  perfis, os scripts (`local-env.sh`, `check-local-profiles.sh`) e regista
  honestamente um desvio conhecido: o compose usa `postgis/postgis:15-3.4`
  porque a imagem `17-3.7` não existe; a verificação PG17+PostGIS é a do
  host — **diferença local conhecida que o passo 4.1 do roteiro deve
  esperar e registar (A10), não descobrir por acaso**.
- **[SEM FONTE] nomeado:** **não foi localizado placar numérico arquivado**
  de fumaça/carga do ambiente local (tempos/vazão/erros) utilizável como
  linha de base do A8. O roteiro resolve no passo 0.6: a linha de base é
  produzida localmente antes do ensaio (ou localizada no repositório
  pós-429) e congelada — pendência **P-LC1-04**. Sem ela, o ensaio não
  começa (A8 incumprível por construção).
- **Também [A DEFINIR] para o A8:** casos do roteiro de fumaça, perfil da
  carga curta (duração/concorrência/mistura) e tolerância de comparação
  (P-LC1-03) — o aceite da LC.1 manda comparar, não fixa nenhum dos três.

## 6. IaC do ensaio — [A DEFINIR] estrutural (ver passo 0.5)

- Não existe ficheiro de IaC (`.tf` ou equivalente) em todo o workspace
  local conferido (busca por `*.tf`: **zero resultados** fora de outputs
  de agentes). O Terraform antigo apontava para `us-east-1` e foi
  arquivado (ADR-0010, via ADR-0011). A IaC definitiva é a **LC.2, que
  depende da LC.1** — dependência circular apenas aparente: a LC.1 precisa
  de uma **IaC mínima do ensaio**, distinta da IaC de ambientes da LC.2.
  Pendência **P-LC1-02** (autor/recorte da IaC do ensaio ou antecipação
  expressa do recorte da LC.2), a resolver **antes** da Fase 0 fechar.

## 7. Conta AWS de ensaio (`sa-east-1`) — PENDÊNCIA EXTERNA CENTRAL

- **P-LC1-01:** não existe conta AWS neste bloco (regra dura do bloco:
  proibido AWS). A criação/autorização da conta de ensaio, o seu custo e
  o teto de gasto são **do dono/projeto** — fator externo nomeado. Sem
  ela, **nenhum** passo das Fases 1–6 do roteiro roda; as Fases 0.1–0.3 e
  0.5–0.7 (conferências e preparações locais) podem e devem fechar antes.

## Síntese dos pré-requisitos

| Pré-requisito | Estado em 09/11/2026 | Fecha o passo |
|---|---|---|
| A1 fases/ondas (incl. B2; excl. pendentes/sob demanda e LC.15) | **Não concluído** (262/286 pré-bloco; bloco em produção; pousos não reverificados — 429) | 0.1 |
| A2 GL.4 | **Não concluído** (dossiê em produção; decisão do dono pendente) | 0.2 |
| A3 ED-01 | **Não concluído** (verificação em produção; pasta `ed-01/` vazia) | 0.3 |
| A5 conta AWS de ensaio | **Pendente externa** (dono/projeto) — P-LC1-01 | 0.4 |
| A6 IaC do ensaio | **[A DEFINIR]** — P-LC1-02 | 0.5 |
| A8 linha de base P0.27 | Ambiente documentado; placar **[SEM FONTE]** — P-LC1-04 (+ tolerâncias P-LC1-03) | 0.6 |
| A7 suíte P0.45 | Desenho e impl. local documentados; artefacto da suíte **[SEM FONTE]** local — P-LC1-08 | 0.7 |

**Conclusão dos pré-requisitos:** nenhum dos sete está fechado à data
deste pacote — o que é o resultado esperado de um pacote de **preparação**
produzido em paralelo às frentes de que depende. O ensaio só começa com a
Fase 0 integralmente aprovada.

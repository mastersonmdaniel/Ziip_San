# 21 — Matriz viva de requisitos de editais

Matriz viva da P1.25 (ED-01), no formato do consolidado 18 §7. Arquivo:
[`matriz-viva.csv`](matriz-viva.csv), versionado no repositório e conferido
pelo script [`scripts/check-matriz-viva.py`](../../../scripts/check-matriz-viva.py)
(ver "Conferência" abaixo). Ela substitui, para acompanhamento, as tabelas
estáticas de 18d a 18h — que continuam como fonte e não foram alteradas.

## Formato (consolidado 18 §7.1)

| Campo | Conteúdo |
|---|---|
| `requirement_id` | ID na extração (`TR-221`, `SG-220`, `ED-019`). Normalizado com zero à esquerda até 3 dígitos (`TR-69` das matrizes = `TR-069` das extrações), para que o mesmo requisito tenha um só ID |
| `canonical_id` | Requisito canônico (`RC-nnnn`) que agrupa os equivalentes entre editais do mesmo modelo. Derivação: uma linha das matrizes 18d–18h já junta os equivalentes (ex.: `TR-129, SG-066`); todos os IDs da mesma linha recebem o mesmo `RC` |
| `tender` | Edital (`VISAN-PE-06-2026` para `TR`/`ETP`/`PP`, `SAMAE-PE-2-2026` para `SG`; `ED` = itens do edital/ETP da VISAN, classificados como VISAN) |
| `section`, `area` | Subseção da matriz e área (cadastro e medição; faturamento e cobrança; atendimento e canais; operação, qualidade e BI; não funcionais e habilitação). 18d §2 não tem subseções; `section` fica vazio nessas linhas |
| `poc` | `V:POC-nn` (faixa do roteiro [19](19-ROTEIRO-POC-VISAN.md)), `S:critical`, `S:essential`, `S:assessable`, `V:possible`, `V:POC-rules` (as 8 linhas TR-038–TR-045, que são as regras da POC, não itens), `none`, ou `PENDENTE` |
| `code_status` | `MEETS`, `PARTIAL`, `MISSING` (snapshot das matrizes 18d–18h, 12/10/2026) ou `PENDENTE` (6 linhas sem marcador de situação na fonte) |
| `plan_raw` | Texto da coluna "Plano" da linha-fonte (tarefas do plano v2.2). Não é a tarefa vigente: a coluna `task_phase` é a derivada atual |
| `delivery`, `task_phase` | ED-nn e fase/onda da ED, derivados do consolidado 18 §5.1 (ver "Derivação" abaixo); `PENDENTE` quando nenhuma faixa de ED cobre o ID |
| `tests` | Nomes dos testes automatizados que provam o requisito. Vazio: a regra do §7.2 (etiqueta `@Tag("req:SG-221")` / `[SG-221]` no título) ainda não existe no código — verificado: nenhum teste usa essas etiquetas. Preenchimento pelo script do CI, quando as etiquetas existirem |
| `rehearsal` | Data, fase e resultado do último ensaio do item (2/1/0 na VISAN; atende/não atende no SAMAE). Vazio: o primeiro ensaio é ao fim do P1 (P1.25) — PENDENTE |
| `legal_note` | Referência ao consolidado 18 §6 quando houver conflito (ex.: `L1`), e o protocolo do pedido de esclarecimento — protocolo PENDENTE em todos os casos (nenhum pedido foi protocolado) |
| `poc_origin` | Proveniência do campo `poc` (extra, fora do §7.1, para auditoria da derivação) |

## Derivação (como cada campo foi obtido — sem julgamento manual)

1. **Linhas-fonte:** todas as linhas das tabelas de 18d–18h §2, com IDs
   expandidos (faixas `TR-1082 a TR-1087` e `TR-068–TR-092` expandidas).
2. **Deduplicação:** 3.362 menções → 2.663 IDs únicos (699 menções repetidas
   entre matrizes; o consolidado 18 §2.2 apurou 584 citações repetidas pelo
   critério dele — a diferença vem do critério de contagem, não de conteúdo:
   aqui conta cada menção em linha de matriz, inclusive as de 18h). Primeira
   ocorrência mantida, na ordem 18d → 18e → 18f → 18g → 18h, conforme a regra
   de 18d §0 para a revisão de consumo ("se a matriz de faturamento também a
   cobrir, vale a desta"). As sobreposições conhecidas estão no consolidado
   18 §2.2 (relatórios em 18d/18e/18g; revisão de consumo e emissão em campo
   em 18d/18e; ajustes de fatura e débito automático em 18e/18f).
3. **Total:** 2.663 IDs únicos — igual ao total de distintos do consolidado
   18 §2.2 (1.114 TR + 1.415 SG + 90 ED + 21 ETP + 23 PP = 2.663).
4. **Situação no código:** primeira ocorrência (não a mais favorável, como o
   consolidado §2.2). Por isso os totais diferem levemente: aqui MEETS 90 /
   PARTIAL 537 / MISSING 2.030 / PENDENTE 6; lá Atende 91 / Parcial 560 /
   Não atende 2.010 / 2 sem situação. Reconciliar os 6 PENDENTE (ED-024,
   ED-037, SG-1370, SG-1371, SG-1375, SG-1376, todos de 18f, sem marcador na
   linha-fonte) e a regra "mais favorável × primeira ocorrência" é pendência
   da próxima revisão da matriz, não ajuste forçado agora.
5. **`delivery`:** faixas de "Requisitos cobertos" do consolidado 18 §5.1 (67
   EDs), com `exceto` aplicado em duas passadas (listas de IDs e referências
   a outras EDs, ex.: "exceto os da ED-45"). 545 IDs ficam com `PENDENTE`
   (nenhuma ED os cobre) — conceito distinto do "não planejado" do
   consolidado §2.2 (1.254), que mede cobertura pelo plano v2.2, não por ED.
6. **`poc`:** TR pela coluna POC de 18a §2 (alínea → faixa POC-nn do roteiro
   19; `Possível` → `V:possible`; `Não` → `none`); SG pela coluna
   Obrigatoriedade de 18c §4 (crítico/essencial/avaliável). `S:critical` =
   1.037, igual ao "cerca de 1.037" do consolidado 18 §3.2. ED/ETP/PP ficam
   `PENDENTE` (134): as fontes não lhes dão classificação POC.
7. **`legal_note`:** itens do consolidado 18 §6 (24 requisitos com nota L1–L20;
   os modelos de pedido estão em [22](22-MODELOS-JURIDICOS.md)).

## Conferência

O script `scripts/check-matriz-viva.py` (só conferência de documentos, não
código de produto) verifica, sem depender de banco de dados:

- cabeçalho exato do CSV e ausência de `requirement_id` duplicado;
- todo `requirement_id` existe nas tabelas de 18d–18h §2 (com a mesma
  normalização de zeros);
- todo `delivery` é uma ED do consolidado 18 §5.1;
- `poc` só usa o vocabulário do §7.1 (mais `PENDENTE`);
- o roteiro [19](19-ROTEIRO-POC-VISAN.md) contém os 81 itens POC-01–POC-81 e
  o roteiro [20](20-ROTEIRO-POC-SAMAE.md) contém as seções críticas;
- links relativos de 19–23 resolvem para arquivos do repositório.

Como rodar: `python3 scripts/check-matriz-viva.py` (a partir da raiz do
repositório). **Fiação no CI: PENDENTE** — não há hoje nenhum job de CI que
cubra `docs/` (verificado no `.github/workflows/ci.yml`: jobs de backend,
frontend, segredos, licenças e scripts, nenhum de documentos; a validação
citada na P1.21 cobre `content/help`, não `docs/`). Cabe ao coordenador
decidir a fiação (uma linha no job de scripts, no mesmo padrão dos demais
checks), para não conflitar com outras frentes que tocam o CI. O check roda
localmente até lá — resultado desta entrega na seção "Verificação" do
relatório da frente.

## Como se mantém viva (consolidado 18 §7.2)

1. **Requisito → tarefa.** Toda tarefa do plano que toca requisito de edital
   cita a ED no campo "Origem"; a matriz liga o requisito à ED e à tarefa.
   Requisito sem tarefa aparece no relatório como "não planejado".
2. **Tarefa → teste.** Testes que provam requisito levam o ID no nome ou em
   etiqueta (`@Tag("req:SG-221")` no JUnit; `[SG-221]` no título do
   Playwright). Um script do CI lê as etiquetas e preenche `tests`; requisito
   marcado `MEETS` sem teste é recusado. **Estado atual: nenhuma etiqueta
   existe — PENDENTE.**
3. **Teste → roteiro de POC.** Cada item do roteiro lista os requisitos e os
   testes que o sustentam; o ensaio de fim de fase registra o resultado em
   `rehearsal`. O relatório de cobertura (por edital, área, onda e item
   crítico) é gerado a cada aceite de fase e anexado ao diário.
4. **Gatilhos de atualização:** fechamento de tarefa (diário de bordo), aceite
   de fase (ensaio), ADR novo ou alterado, edital novo.

## Regra para novos editais (consolidado 18 §7.3)

1. Extração no formato de 18a e 18c (identificação, itens e preços, uma linha
   por requisito com ID de prefixo próprio do edital, página e item; regras
   da POC; habilitação; SLA e penalidades; integrações; pontos ambíguos).
   Prazo: até 3 dias úteis da publicação.
2. Equivalência: cada requisito é ligado a um `canonical_id` existente quando
   o texto for equivalente (os editais de saneamento de SC derivam do mesmo
   modelo); só o que não tiver equivalente gera requisito canônico novo.
3. Matriz por área, nas cinco áreas acima, com as mesmas colunas e o mesmo
   vocabulário de situação; requisito novo entra numa ED existente ou gera ED
   nova, com origem, esforço, dependências e ADR.
4. Conflitos legais conferidos contra o §6; pedido de esclarecimento ou
   impugnação protocolado no prazo do edital (modelos em
   [22](22-MODELOS-JURIDICOS.md)).
5. Decisão de disputar (go/no-go), registrada no diário antes do prazo de
   impugnação: habilitação completa (atestados, balanço, certidões),
   resultado do último ensaio contra o roteiro do edital, prazo de implantação
   contra a metodologia (ED-09), preço contra a tabela de custo (ED-10).

## Ressalvas e pendências

- **Ambiguidade de namespace `ED-`:** nas matrizes, `ED-019` etc. são itens do
  edital/ETP da VISAN (18b); no consolidado §5.1, `ED-01` etc. são entregas.
  O mesmo prefixo, dois significados. A matriz viva mantém os dois (um como
  `requirement_id`, outro como `delivery`) e esta nota registra a colisão —
  não foi renomeado nada, para não quebrar a rastreabilidade com as fontes.
- **`code_status` é snapshot de 12/10/2026**, não estado atual do código:
  revalidar a cada aceite de fase (gatilho 4 acima).
- **`task_phase` é a fase/onda da ED**, não a tarefa nominal do plano v3
  (ex.: P1.3, P2.20): a tarefa nominal por requisito está em `plan_raw`
  (v2.2) e, por alínea/seção, nos roteiros 19 e 20. Unificar tarefa nominal
  por ID é pendência da próxima revisão.
- **Primeiro ensaio ao fim do P1: PENDENTE** (registro no diário, pelo
  coordenador; este documento só define o formato).
- **Fiação do check no CI: PENDENTE** (decisão do coordenador; ver
  "Conferência").

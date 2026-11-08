# GL.2 — Plano do paralelo local: dois ou três ciclos de faturamento, conta a conta

> **Tarefa:** GL.2 "Dois ou três ciclos de faturamento paralelo local,
> conta a conta" (ADR-0001; ADR-0033), tamanho G, dependências GL.1,
> P2.20, P3.22, P3.29, P3.30, P2.31 e P2.32. **Frente consumidora
> imediata:** GL.4 (aceite da homologação local, que anexa o relatório
> do paralelo). **Fronteira:** o paralelo com dado real é a LC.11, na
> produção; aqui é o ambiente local, sobre a carga anonimizada da GL.1.
>
> **Estado honesto desta entrega:** o **aparato completo do ensaio**
> (orquestrador fail-closed, validador da config, comparador conta a
> conta, roteiro, checklist e modelo de registro), provado nesta VM pelo
> harness com stubs (47 verificações). **Nenhum ciclo real foi
> executado** — não há piloto escolhido (P0.30 não entregue), carga da
> GL.1 executada, Docker nem JDK nesta VM. O primeiro registro real de
> ensaio nasce da execução no PC do dono, seguindo o roteiro.

## 1. O que é o paralelo local (e o que ele não é)

Sobre o ambiente de homologação local da GL.1 (perfil `homolog` da
P0.27, carga anonimizada na origem com código substituto), rodam-se
**dois ou três ciclos de faturamento completos** no ZiipSan e compara-se
**cada conta** com a conta correspondente do sistema do piloto, **pelo
código substituto** — a correspondência código ↔ matrícula nunca sai do
piloto (ADR-0033). O sistema do piloto continua oficial e emissor; o
paralelo não emite fiscal de produção (`tpAmb=1` é recusado em dois
portões: config e compose) e não substitui a LC.11 (paralelo com dado
real, na produção).

## 2. Peças e donos (nada é reimplementado aqui)

| Peça | Dono | Papel na GL.2 |
|---|---|---|
| Ambiente + carga anonimizada | GL.1 / P0.27 / importadores P1.2, P2.10, P2.19, P3.26 | pré-condição: registro da carga **CONCLUIDO** (portão do pré-voo) |
| Motor de faturamento (medição, ciclo, conta) | P2.5 (+ P2.31/P2.32 aplicados por ele) | gera as contas de cada ciclo, via hook de faturamento |
| Perfil regulatório da agência do piloto | P2.31 (RAS-01) | config declara agência/perfil; validador exige **ativo** e **vigência cobrindo o 1º ciclo**, **conferida antes** dele |
| Conteúdo mínimo da fatura por lei e por agência | P2.32 | conferido **em cada conta** de cada ciclo, via hook de conteúdo |
| Critério de divergência | **P0.30** (escolha do piloto) | a config carrega as tolerâncias **com a fonte declarada**; sem o bloco ou com fonte diversa, o pré-voo recusa — esta entrega **não inventa tolerância** |
| Retificação de conta | P2.20 | corrige as divergências explicadas dentro do critério, via hook, antes do ciclo fechar |
| Relatórios operacionais | P3.22 | conferem a correção (hook) e registram a divergência quando o ciclo reprova |
| Devoluções e pagamentos não identificados | P3.29 | tratam as ocorrências declaradas de cada ciclo, via hook obrigatório quando há ocorrência |
| Unidade organizacional e escopo por área | P3.30 | a operação do paralelo roda sob a unidade/área declaradas na config (portão do pré-voo) e expostas aos hooks (`GL2_ORG_UNIDADE`/`GL2_ESCOPO_AREA`) |
| NFAg-e em homologação/simulador e limpeza de XML | P2.6 / P3.7 | `tpAmb=2` ou simulador; XML assinados só na área separada do ciclo e **apagados ao fim de cada ciclo**, com exclusão registrada (quantidade + hash do lote) |
| Orquestrador, validador e comparador da GL.2 | esta entrega (`scripts/paralelo/`) | sequência, portões, comparação conta a conta e registro do ensaio de POC |

**Por que hooks e não chamadas diretas:** o motor de faturamento e os
serviços das dependências são serviços Java do produto, **sem CLI neste
repo** — o mesmo fato e o mesmo desenho da GL.1 (importadores). Cada
etapa roda o comando que a integração fiar em `ZIIPSAN_GL2_CMD_*`; sem
fiação, a etapa **reprova declarando a pendência** (saídas 3/4/7
conforme a etapa) — nada é fingido. O comparador e o validador, esses
sim, são ferramentas de operação entregues prontas (Python, biblioteca
padrão), como o validador de manifesto da GL.1.

## 3. Sequência de um ciclo (o orquestrador aborta na primeira falha)

| # | Etapa | Critério | Saída se reprovar |
|---|---|---|---|
| 0 | Pré-voo (uma vez, antes do ciclo 1) | config válida; GL.1 CONCLUIDO; fiscal de produção desligado no compose (fonte que controla a variável); contas do piloto presentes em todos os ciclos | 2 |
| 1 | Faturamento | hook gera `contas-ziipsan.json` do ciclo | 4 |
| 2 | Conteúdo mínimo (P2.32) | hook confere cada conta do ciclo | 3 |
| 3 | Escopo (P3.30) | hook, quando fiado, sai 0; a declaração do escopo é portão do pré-voo | 7 |
| 4 | Comparação conta a conta | comparador: divergência **dentro do critério da P0.30** e **toda explicada** | 5 |
| 5 | Retificação + relatórios | havendo divergência (mesmo dentro do critério): hooks da P2.20 e da P3.22 executados com sucesso | 7 |
| 6 | Devoluções / não identificados (P3.29) | ocorrências declaradas do ciclo tratadas pelo hook | 7 |
| 7 | Fechamento fiscal (P3.7) | nenhum XML fora da área separada; XML da área apagados, exclusão registrada | 6 |
| 8 | Registro | `docs/paralelo/paralelos/<carimbo>-gl2/registro-paralelo.md` + artefatos por ciclo | — |

## 4. Regras da comparação (comparar-contas.py)

- **Chave:** `codigoSubstituto` — duplicidade em qualquer lado é
  **entrada inválida** (saída 3 do comparador), nunca "sem divergência".
- **Divergência de valor:** diferença absoluta de `totalCentavos` por
  conta; o critério (da config, fonte P0.30) limita a diferença **por
  conta**, a **soma do ciclo** e o **número de ausências** (conta de um
  lado só).
- **Toda divergência exige explicação** não vazia no
  `explicacoes.json` do ciclo (por código substituto) — inclusive as
  dentro do critério. Sem explicação, o ciclo reprova.
- **Consumo e vencimento:** quando presentes nos dois lados e
  diferentes, viram divergência registrada que exige explicação; não há
  tolerância monetária para eles na spec, logo **não reprovam sozinhos
  o critério monetário** — interpretação declarada, não escondida. Se o
  critério da P0.30 tratar consumo/vencimento, ele deve ser refletido
  na config antes da execução (pendência nomeada no relatório da
  frente).
- **Divergência não se ajusta:** fora do critério ou sem explicação, o
  relatório fica gravado ao lado do registro, o hook de relatórios
  (P3.22) é chamado como evidência quando fiado, e o ciclo reprova. A
  correção é a retificação da P2.20 num novo ciclo/execução — não uma
  edição do relatório.

## 5. Papéis

| Quem | Faz |
|---|---|
| Piloto | mantém o sistema oficial emissor; fornece, por ciclo, as contas pelo código substituto (`contas-piloto.json`) e as explicações das divergências conhecidas; guarda a correspondência código ↔ matrícula |
| Operador do paralelo | monta a config (critério copiado do registro da P0.30, perfil da P2.31, escopo da P3.30), declara as ocorrências por ciclo, roda o orquestrador e assina o registro |
| Integração (projeto) | fia os comandos `ZIIPSAN_GL2_CMD_*` aos serviços reais e roda a CI total da camada |

## 6. Critérios de aceite da GL.2 → onde são provados

Ver a tabela item a item no `RELATORIO-GL.2.md` da frente. Resumo: os
portões, a comparação, a ordem das etapas e a limpeza fiscal são
provados **aqui** pelo harness com stubs; os ciclos em si — faturamento
real sobre a carga da GL.1, contas reais do piloto, retificações e
relatórios reais — são provados **na execução** no PC do dono,
pendentes por natureza (e por dependerem da P0.30), declarados, nunca
afirmados como feitos.

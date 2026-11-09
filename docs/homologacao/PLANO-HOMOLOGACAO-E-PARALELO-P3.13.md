# P3.13 — Plano da homologação local e do paralelo real

> **Produtor:** sessão `ca3d8c13-25d9-449d-828d-89c54bbff8fd` (papel: Produtor)
> **Data:** 09/11/2026 · **Bloco:** conclusão do ZiipSan · **Sem acesso ao GitHub neste bloco (parada dura por HTTP 429) — trabalho 100% local.**
> **Destino no repo:** `docs/homologacao/PLANO-HOMOLOGACAO-E-PARALELO-P3.13.md` (convenção dos pacotes vizinhos: GL.1 em `docs/homologacao/`, GL.2 em `docs/paralelo/`; este plano cobre a homologação local e serve de ponte para o paralelo — fica em `docs/homologacao/`).

**Tarefa (fonte única, caractere a caractere):** P3.13 — "Plano da homologação local e do paralelo real"; origem `ADR-0001; ADR-0033`; tamanho `P`; deps: `P0.30`; aceite: *"Critérios da homologação local (GL.2) e plano preliminar do paralelo real, do corte e da volta no lançamento (LC.11, LC.13) aprovados pelo piloto, com o registro da aprovação no diário"* — `tarefas-P3.json`, em `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__/`.

---

## 0. Enquadramento honesto (leia antes de tudo)

1. **Não há piloto nem cliente real.** Pela decisão nº 34 do dono (09/11/2026, `DECISOES-DO-DONO.md`, item 34): *"NÃO há cliente real — o produto será vendido em licitação APÓS a conclusão"*. A P0.30 foi redefinida como **base fictícia de testes**: 1 prestadora fictícia e 12.000 unidades consumidoras fictícias nas situações que o sistema oferece, com limpeza posterior obrigatória (pacote `base-ficticia-12k/`, completo localmente: 23 situações somando 12.000 UCs exatos; dois municípios fictícios — Porto das Gaivotas, 9.000 UCs, e Vale do Rio Claro, 3.000 UCs).
2. **Consequência para este aceite:** o sujeito "piloto" do aceite **não existe**. Este documento produz tudo o que é produzível sem ele — critérios (§1) e plano preliminar (§2) — e registra a aprovação como **pendência nomeada** (§3), com o termo em branco pronto. **Nenhuma aprovação é simulada, presumida ou datada neste pacote.**
3. **Consequência para os critérios:** o aceite da P0.30 exigia o *"critério de divergência da homologação local e do paralelo real"* definido **pelo piloto**. Sem piloto, os **limiares numéricos** desse critério não têm fonte: todos aparecem como **[A DEFINIR]** nomeados (§1.3). A **estrutura** do critério (o que se compara, como se dispõe de cada divergência) deriva das fontes do plano e do aparato já entregue e revisado da GL.2 — cada critério cita a sua fonte.
4. **Estado real da GL.2 hoje:** a GL.2 entregou o **aparato completo de ensaio** (orquestrador fail-closed, validador de config, comparador conta a conta, roteiro, checklist, modelo de registro), provado por harness (56/56 após correções da revisão cruzada). A própria GL.2 declara, honestamente: **nenhum ciclo real foi executado**, porque não há critério de divergência registrado nem contas reais de piloto. Este plano **não muda esse fato**: ele fornece a estrutura de critérios que faltava; os ciclos reais continuam pendentes das condições do §2.5.

---

## 1. (a) Critérios da homologação local — para a GL.2

### 1.1. Objeto e chave da comparação

| # | Critério | Fonte |
|---|---|---|
| C-01 | A comparação é **conta a conta**, entre as contas do ZiipSan e as do sistema de referência, pela chave do **código substituto** (a correspondência com a matrícula real nunca entra no ambiente local). | Aceite da GL.2 (*"contas comparadas conta a conta com as do sistema do piloto pelo código substituto"*); ADR-0033 / roteiro da P0.45 via GL.1 |
| C-02 | A homologação local roda **2 ou 3 ciclos** de faturamento paralelo no ambiente local. O número exato de ciclos da rodada é registado na config do ensaio antes do 1º ciclo. | Título e aceite da GL.2 (*"dois ou três ciclos"*); validador da GL.2 (só aceita o inteiro 2 ou 3) |
| C-03 | Na rodada sobre a **base fictícia** (modelo vigente, decisão 34), o "sistema de referência" é a **expectativa derivada da matriz de situações** da base (cada UC fictícia nasce de uma situação do domínio com resultado esperado derivável); a comparação continua conta a conta pelo código substituto. Divergência contra a expectativa é divergência para todos os efeitos deste §1. | Decisão 34; `base-ficticia-12k/MATRIZ-SITUACOES.md` (cada situação com fonte no domínio; soma mecânica 12.000) |
| C-04 | Antes do 1º ciclo: perfil regulatório da agência (P2.31, RAS-01) **ativo**, com **vigência cobrindo o 1º ciclo** e **conferido estritamente antes** dele (`conferidoEm < dataPrimeiroCiclo`); na base fictícia, o perfil é o da prestadora fictícia, sem agência real presumida — agência concreta: **[A DEFINIR]** quando houver cliente (pendência P3.13-04). | Aceite da GL.2; correção R1 da revisão da GL.2 (leitura estrita de "conferida antes") |
| C-05 | O **conteúdo mínimo da fatura por lei e por agência** (P2.32) é conferido **em cada conta** do paralelo; conta sem conferência de conteúdo não fecha o ciclo. | Aceite da GL.2 |
| C-06 | NFAg-e somente no simulador ou na homologação da SEFAZ (`tpAmb=2`); `tpAmb=1` recusa a rodada; XML assinados ficam só na área separada e são **apagados ao fim de cada ciclo**, com quantidade e hash do lote registados. | Aceite da GL.2 (remissão à P3.7); portões provados no harness da GL.2 |

### 1.2. Tipos de divergência e disposição (conta a conta)

Tipos = os que o comparador entregue (`gl.2/entrega/scripts/paralelo/comparar-contas.py`) de facto distingue; disposição = a do aceite da GL.2 executada pelo orquestrador entregue.

| Tipo | Como se detecta | Disposição obrigatória |
|---|---|---|
| **VALOR** — `totalCentavos` difere entre os lados | diferença absoluta em centavos, por conta | Explicação registada **sempre** (C-08). Dentro dos limiares (L-01/L-02) **e** explicada → divergência aceite no ciclo, com retificação pela P2.20 e conferência pelos relatórios da P3.22 antes do fechamento. Fora de qualquer limiar **ou** sem explicação → **o ciclo não fecha** (orquestrador sai 5 e grava o relatório ao lado do registro). |
| **AUSÊNCIA** — conta existe num lado e não no outro | código substituto presente num só índice; duplicidade de código = entrada inválida (saída 3), nunca "sem divergência" | Conta como ausência para o limiar L-03. Explicação registada **sempre**. Acima de L-03 **ou** sem explicação → **o ciclo não fecha**. Ausência nunca é tratada como conta igual. |
| **CONSUMO** — `consumoM3` presente nos dois lados e diferente | comparação direta do campo | Explicação registada **sempre**. Não tem tolerância monetária própria nas fontes: **não reprova sozinha** o critério monetário (interpretação declarada no plano da GL.2 e no cabeçalho do comparador). Se o critério futuro (piloto/dono) lhe der limiar, ele entra como **[A DEFINIR]** (pendência P3.13-03) e a config deve refleti-lo. |
| **VENCIMENTO** — `vencimento` presente nos dois lados e diferente | comparação direta do campo | Idem CONSUMO: explicação sempre; sem limiar próprio nas fontes — **[A DEFINIR]** (pendência P3.13-03). |

Regras de fechamento do ciclo (todas com fonte no aparato GL.2 provado):

| # | Critério | Fonte |
|---|---|---|
| C-07 | **Toda** divergência — de qualquer tipo — tem de estar **explicada** (motivo não vazio, por código substituto). Divergência dentro dos limiares mas **sem explicação reprova** o ciclo (comparador sai 2; orquestrador sai 5). | Aceite da GL.2 (*"divergência dentro do critério da P0.30, toda explicada"*) e da LC.11 (mesma fórmula) |
| C-08 | Divergência **fora** dos limiares reprova o ciclo **mesmo explicada** (comparador sai 1). Não existe "aceite por justificativa" acima do limiar sem decisão formal registada de quem aprova (§3). | Comparador da GL.2 (veredito = dentro **e** toda explicada) |
| C-09 | Divergências corrigidas são **retificadas pela P2.20** e **conferidas pelos relatórios da P3.22** dentro do próprio ciclo; ciclo com divergência explicada e dentro dos limiares só fecha com os dois hooks executados. | Aceite da GL.2 |
| C-10 | Devoluções e pagamentos não identificados ocorridos no paralelo são tratados pela **P3.29** no ciclo; ocorrência declarada sem tratamento reprova (saída 7). | Aceite da GL.2 |
| C-11 | A operação do paralelo respeita **unidade organizacional e escopo por área** (P3.30) declarados na config; config sem escopo é recusada no pré-voo. | Aceite da GL.2; validador da GL.2 |
| C-12 | O ensaio grava **registro de POC** com os valores medidos (ciclos concluídos, divergências por tipo, somas, limpezas fiscais) — registro CONCLUIDO com menos de 2 ciclos efetivamente comparados é impossível por construção (guarda do orquestrador). | Aceite da GL.2 (*"ensaio de POC registrado"*); correção D1 da revisão da GL.2 |
| C-13 | Pré-condição de qualquer ciclo: carga da **GL.1 concluída** (linha de resultado `CONCLUIDO` no registro da carga — âncora exata, não substring) e config validada; sem isso o pré-voo recusa (saída 2). | Orquestrador da GL.2 (correção D2 da revisão); GL.1 |

### 1.3. Limiares — todos [A DEFINIR], nomeados

O critério da P0.30 devia fixar os números; a P0.30-piloto não aconteceu (decisão 34). **Nenhum número abaixo foi inventado**: a estrutura é a que o comparador/validador entregues exigem, e cada valor é pendência nomeada.

| Limiar | Significado (estrutura, com fonte) | Valor |
|---|---|---|
| **L-01** `toleranciaCentavosPorConta` | maior diferença de valor aceite **por conta**, em centavos | **[A DEFINIR]** — pendência **P3.13-01**: fixação pelo dono do produto (enquanto não houver piloto) ou pelo piloto futuro; sem ele, o validador da GL.2 recusa a config e nenhum ciclo roda |
| **L-02** `toleranciaCentavosTotalCiclo` | maior soma das diferenças absolutas aceite **no ciclo**, em centavos | **[A DEFINIR]** — pendência **P3.13-01** (mesma fixação de L-01) |
| **L-03** `toleranciaAusencias` | maior número de contas ausentes aceite **no ciclo** | **[A DEFINIR]** — pendência **P3.13-01** (mesma fixação de L-01) |
| **L-04** limiar de consumo/vencimento | eventual tolerância própria para os tipos CONSUMO/VENCIMENTO | **[A DEFINIR]** — pendência **P3.13-03**: hoje, pelas fontes, esses tipos exigem explicação e não reprovam sozinhos; criar limiar é decisão de quem aprova |

Nota de honestidade: com L-01/L-02/L-03 em [A DEFINIR], **a GL.2 continua sem poder executar ciclos reais** — este plano entrega a estrutura completa e executável do critério (o comparador já a consome), mas o aceite integral da GL.2 ("divergência **dentro do critério**") só se prova quando os limiares forem fixados por quem de direito e os ciclos rodarem. Declarar o contrário seria inflar.

### 1.4. O que muda na homologação com a base fictícia (vigente) × piloto real (futuro)

| Aspecto | Base fictícia (hoje, decisão 34) | Com cliente real pós-licitação |
|---|---|---|
| Massa | 12.000 UCs fictícias, 23 situações do domínio, 2 municípios fictícios | Cadastro e histórico reais **anonimizados na origem** pelo roteiro da P0.45, conciliados com os totais do sistema do cliente (aceite da GL.1) |
| Referência da comparação | Expectativa derivada da matriz de situações | Contas do sistema legado do cliente, pelo código substituto |
| Limiares L-01…L-04 | Fixação pelo **dono do produto** (não há piloto) | Fixação **com o piloto**, como previa a P0.30 original |
| Rede real | Não existe; só estrutura fictícia | Carga da rede real autorizada (redes, peças e estruturas, **sem** ligações nem imóveis reais), acesso restrito (ADR-0033) |
| Resultado | Ensaio interno de homologação; **não** gera atestado nem aceite de cliente | Homologação local da GL.2 nos termos originais do plano |

---

## 2. (b) Plano preliminar do paralelo real, do corte e da volta — LC.11 / LC.13

Caráter: **preliminar**, como pede o aceite. Fontes: aceites integrais da LC.11 e da LC.13 em `tarefas-GL-P4-LC-PEND-C.json`; aceite da GL.3 (restauração) para a volta; decisão 34 para o enquadramento temporal (produção só com cliente, pós-licitação).

### 2.1. Paralelo com dado real — LC.11

Aceite da LC.11 (fonte): *"um ou dois ciclos de paralelo na produção, conta a conta contra o legado; divergência dentro do critério da P0.30, toda explicada; diferenças novas em relação à homologação local (GL.2) registradas; sem emissão fiscal de produção; com optantes, débito automático conferido"*.

Sequência preliminar:

1. **Pré-condições:** LC.10 concluída (deps da LC.11); homologação local (GL.2) executada e aceite da GL.4 registado (dossiê da GL.4 é a base documental — ver pacote GL.4); limiares L-01…L-03 fixados (P3.13-01) — o critério é **o mesmo** da homologação local, salvo decisão formal em contrário registada.
2. **Execução:** 1 ou 2 ciclos de faturamento em produção, em paralelo ao legado, que **continua oficial e emissor** durante todo o paralelo (ver GL.4, declaração (c)).
3. **Comparação:** conta a conta contra o legado, mesmos tipos e mesma disposição do §1.2 (VALOR/AUSÊNCIA/CONSUMO/VENCIMENTO; toda divergência explicada; fora do limiar não fecha o ciclo).
4. **Diferenças novas** em relação à homologação local (tipos, causas ou magnitudes que a GL.2 não viu) são **registadas à parte** — é item expresso do aceite da LC.11.
5. **Fiscal:** **sem emissão fiscal de produção** no paralelo (`tpAmb` de produção desligado); a virada fiscal é ato do corte (LC.13), não do paralelo.
6. **Débito automático (com optantes):** conferido no paralelo — remessa/retorno FEBRABAN 150 com arquivos de teste **sem envio ao banco** já foram conferidos na GL.4; no LC.11 a conferência é sobre o comportamento em produção paralela, nos termos do aceite. Sem optantes, regista-se que a P3.24 não se aplica (ramo documentado na GL.4).
7. **Saída:** relatório do paralelo real, conta a conta, que é o insumo da decisão de corte (LC.13). Ciclo reprovado pelo critério → o paralelo **não** avança para o corte; volta-se à retificação (P2.20) e a novo ciclo, dentro do limite de ciclos do aceite (1–2); esgotados sem aprovação, a decisão é de **não cortar** e registar (a volta do §2.3 aplica-se por antecipação: nada foi cortado, o legado segue oficial).

### 2.2. Corte do legado: primeira produção — LC.13

Aceite da LC.13 (fonte): *"decisão formal de corte registrada; emissão fiscal ligada (`tpAmb=1`) com aprovação dupla (FISCAL-PORTE-JAVA §3.6); com optantes no piloto (P3.24, condicional), débito automático em produção pela P3.24; sem optantes, o registro do corte informa que a P3.24 não se aplica; legado do piloto desligado (M15)"*.

Sequência preliminar:

1. **Decisão formal de corte registada** — ato do dono do produto e do cliente; este plano não a contém nem a presume. Sem decisão registada, não há corte.
2. **Portão fiscal:** emissão fiscal ligada (`tpAmb=1`) **somente** com **aprovação dupla** (FISCAL-PORTE-JAVA §3.6, citado pelo aceite). Quem são os dois aprovadores concretos no cliente futuro: **[A DEFINIR]** (pendência P3.13-05).
3. **Débito automático:** com optantes, entra em produção pela P3.24 (legado FEBRABAN 150 mantido para a base atual; débito **novo** só por Pix Automático — decisão do dono, item 9: caminho duplo Pix Automático + FEBRABAN 150 legado); sem optantes, o registro do corte declara que a P3.24 não se aplica. Datas de desligamento do legado: deduzidas da regra do BCB pela classe da P3.24 (`BcbDebitRegimeRules`) — nunca digitadas.
4. **Desligamento do legado (M15):** só **depois** dos passos 1–3 registados; o legado é desligado como sistema oficial/emissor no ato do corte, e o ZiipSan assume como oficial. Janela e horário do corte: **[A DEFINIR]** com o cliente (pendência P3.13-05).
5. **Congelamento de mudanças:** entre a decisão (passo 1) e o corte, nenhuma mudança de critério, limiar ou perfil regulatório sem novo registo — **[A DEFINIR]** o responsável pelo congelamento no cliente futuro (pendência P3.13-05).

### 2.3. A volta (rollback do lançamento)

A "volta" do aceite da P3.13 é o caminho de retorno se o corte falhar. Desenho preliminar, só com peças que já existem nas fontes:

1. **Até ao ato do corte, a volta é trivial por construção:** durante o LC.11 o legado continua oficial e emissor e o ZiipSan não emite fiscal de produção — abortar o lançamento antes do corte = encerrar o paralelo e registar; nenhum dado de produção foi emitido pelo ZiipSan.
2. **Gatilhos da volta após o corte** (qualquer um, registado): (i) divergência fora dos limiares L-01…L-03 no primeiro ciclo pós-corte sem retificação possível no ciclo; (ii) falha da emissão fiscal de produção (`tpAmb=1`) sem contingência; (iii) falha do débito automático em produção com optantes. Limiares quantitativos próprios para (ii)/(iii): **[A DEFINIR]** (pendência P3.13-06).
3. **Mecânica da volta:** (a) religar o legado como oficial/emissor (ele só é **desligado** no corte — a volta exige que o desligamento M15 seja **reversível** durante a janela de estabilização; duração da janela: **[A DEFINIR]**, pendência P3.13-06); (b) restaurar o ZiipSan ao ponto pré-corte pelo mecanismo ensaiado na **GL.3** (restauração cronometrada contra o RTO de referência de 4 h do aceite da GL.3; restauração seletiva de um tenant e desfazer de operação em massa da P3.33) — a meta formal de RTO vale na LC.12, não aqui; (c) contas emitidas pelo ZiipSan entre o corte e a volta são tratadas pela retificação da P2.20 e registadas uma a uma — procedimento detalhado: **[A DEFINIR]** no plano definitivo da LC.13 (pendência P3.13-06).
4. **Decisão da volta:** formal e registada, dos mesmos signatários da decisão de corte. Volta executada sem registo não existe para o plano.
5. **Pós-volta:** novo paralelo (LC.11) é pré-condição de qualquer novo corte; não se corta duas vezes sobre o mesmo relatório.

### 2.4. O que muda quando houver cliente real pós-licitação (síntese)

- A prestadora fictícia e as 12.000 UCs são **limpas** (limpeza da decisão 34) e **não** migram para o cliente: o cliente entra por carga própria (LC.10) sobre dado real.
- Os papéis hoje vagos — piloto que aprova critérios/plano (P3.13), dono+piloto que decidem o aceite (GL.4) e o corte (LC.13) — são ocupados pelo cliente contratado e pelo dono do produto.
- Os limiares L-01…L-04 e os [A DEFINIR] das pendências P3.13-04/05/06 são fixados **com o cliente**, e este plano preliminar é revisto e promovido a plano do lançamento antes do LC.11.

### 2.5. Condições que ainda separam este plano de qualquer execução real

1. Cliente real contratado pós-licitação (decisão 34) — sem ele, LC.11/LC.13 não têm sujeito.
2. Limiares L-01…L-03 fixados (P3.13-01) e ciclos reais da GL.2 executados sobre massa válida.
3. Aprovação do §3 registada.

---

## 3. (c) Secção de aprovação — pendência nomeada, termo em branco

**O ato "aprovado pelo piloto" NÃO foi realizado e NÃO é simulado neste pacote.**

- **Pendência P3.13-02 (aprovação e registo):** o aceite exige critérios e plano *"aprovados pelo piloto, com o registro da aprovação no diário"*. Não há piloto (decisão 34). A aprovação cabe, por substituição honesta e **ainda não decidida formalmente**: (i) ao **dono como dono do produto**, para a validade interna deste plano preliminar; e/ou (ii) ao **cliente futuro** pós-licitação, para a validade perante o piloto que o aceite nomeia. Qual dos dois atos fecha a P3.13 é **decisão do dono** — este pacote não a toma.
- Enquanto a aprovação não existir, o estado correto desta tarefa é: **documento produzido, aprovação pendente (P3.13-02), registo no diário pendente do mesmo ato**.
- O **Termo de Aprovação** em branco acompanha este plano como artefato separado (`TERMO-APROVACAO-P3.13.md`, no mesmo destino), pronto para preenchimento e assinatura quando o ato ocorrer. Assinatura, nome, cargo e data estão **em branco de propósito**.

---

## Fontes usadas (todas locais, lidas neste bloco)

- `tarefas-P3.json` (P3.13, P3.24) e `tarefas-GL-P4-LC-PEND-C.json` (GL.2, GL.3, GL.4, LC.11, LC.13) e aceite da P0.30 em `tarefas-P0.json` — pasta de fontes do plano v3.0.
- `DECISOES-DO-DONO.md` — item 34 (P0.30 = base fictícia; sem cliente real) e item 9 (débito automático: caminho duplo).
- Pacotes `gl.1/`, `gl.2/`, `gl.3/` — relatórios, revisões e confirmação (aparato, placares e declarações de não-execução real).
- Pacote `base-ficticia-12k/` — matriz de situações e relatório (12.000 UCs; dois municípios fictícios).
- `p3.13/RELATORIO-VERIFICACAO-P3.13.md` — histórico (07/11/2026): verificação que declarou a P3.13 não executável por falta da P0.30-piloto; a decisão 34 supersede o enquadramento, não os fatos que ela provou.

*Nenhum dado, cliente, prazo, cláusula ou número sem fonte foi introduzido: o que não tem fonte está marcado [A DEFINIR]/[SEM FONTE] e nomeado como pendência no relatório do pacote.*

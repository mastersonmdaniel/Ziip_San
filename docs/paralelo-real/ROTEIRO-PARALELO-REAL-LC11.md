# ZiipSan — LC.11 · Roteiro do paralelo com dado real

- **Sessão produtora:** `1fb44147-00cd-4ed1-a19a-ac6f72c78874` — papel: **Produtor**
- **Data de produção:** 09/11/2026
- **Natureza deste documento:** roteiro de execução para o evento real
  de paralelo. **Nenhum ciclo de paralelo real foi executado.** Não há
  cliente/piloto real (decisão do dono, item 34 do
  `DECISOES-DO-DONO.md`: sem cliente real; produto será vendido em
  licitação após a conclusão). Este roteiro não simula ciclo,
  divergência nem resultado.
- **Aceite da LC.11 (fonte única, íntegra):**
  `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json`,
  registro `LC.11` — origem `ADR-0001; ADR-0033`, tamanho G (2),
  deps `LC.10`:

  > "um ou dois ciclos de paralelo na produção, conta a conta contra o
  > legado; divergência dentro do critério da P0.30, toda explicada;
  > diferenças novas em relação à homologação local (GL.2)
  > registradas; sem emissão fiscal de produção; com optantes, débito
  > automático conferido"

---

## 1. Articulação com a P3.13 (plano preliminar do paralelo real)

Este roteiro executa, passo a passo, o **plano preliminar do paralelo
real** da P3.13 — pacote `~/workspace/ziipsan-producao/p3.13/`,
entrega `docs/homologacao/PLANO-HOMOLOGACAO-E-PARALELO-P3.13.md`,
§2.1 (sequência preliminar da LC.11) e §1 (critérios). Referências
cruzadas usadas neste roteiro:

- **Critérios e tipos de divergência:** P3.13 §1.1–§1.2 (C-01…C-13;
  tipos VALOR, AUSÊNCIA, CONSUMO, VENCIMENTO; regras de fechamento
  C-07/C-08/C-09).
- **Sequência do paralelo:** P3.13 §2.1, itens 1–7 (pré-condições,
  execução 1–2 ciclos, comparação, diferenças novas, fiscal, débito
  automático, saída para a decisão de corte da LC.13).
- **Volta por antecipação:** P3.13 §2.3, item 1 — durante a LC.11 o
  legado continua oficial e emissor; abortar antes do corte é encerrar
  o paralelo e registrar (nada foi cortado).
- **Aprovação da P3.13:** pendência **P3.13-02** (aprovação "pelo
  piloto" não realizada nem simulada — não há piloto). Enquanto ela
  estiver aberta, este roteiro é preliminar como o plano que executa.

## 2. Critério de divergência — leitura adotada e fonte (declaração obrigatória)

O aceite da LC.11 pede "divergência dentro do critério da P0.30". A
leitura adotada neste roteiro, declarada expressamente:

1. **O critério original da P0.30 não existe como número.** O aceite
   da P0.30 (`tarefas-P0.json`) exigia o "critério de divergência da
   homologação local e do paralelo real" definido com o piloto. A
   **decisão nº 34 do dono (09/11/2026) redefiniu a P0.30**: não há
   piloto real — a P0.30 virou base fictícia de testes (12.000 UCs) e a
   espera pelo piloto está encerrada. Logo, **nenhum limiar numérico
   "da P0.30" foi jamais fixado**, e nenhum é inventado aqui.
2. **Adota-se a estrutura de critério da P3.13 (§1),** que por sua vez
   deriva do aparato entregue e revisado da GL.2 (comparador conta a
   conta `comparar-contas.py`, validador de config e orquestrador):
   - comparação **conta a conta** contra o legado (aqui com a
     matrícula real, porque o ambiente é a produção — o código
     substituto é a fronteira do ambiente *local*, P0.45/GL.2 C-01);
   - tipos **VALOR, AUSÊNCIA, CONSUMO, VENCIMENTO** (P3.13 §1.2);
   - **toda divergência explicada** (C-07) — divergência dentro dos
     limiares mas sem explicação **reprova o ciclo**;
   - divergência **fora dos limiares reprova o ciclo mesmo explicada**
     (C-08) — não há "aceite por justificativa" acima do limiar sem
     decisão formal registrada de quem aprova;
   - divergências corrigidas são retificadas pela **P2.20** e
     conferidas pelos relatórios da **P3.22** (C-09);
   - devoluções e pagamentos não identificados do paralelo tratados
     pela **P3.29** (C-10); operação com unidade organizacional e
     escopo por área da **P3.30** (C-11).
3. **Os limiares são `[A DEFINIR]` nomeados** (P3.13 §1.3):
   **L-01** tolerância de valor por conta, **L-02** tolerância de
   valor total do ciclo, **L-03** tolerância de ausências no ciclo —
   todos sob a pendência **P3.13-01** (fixação pelo dono do produto
   enquanto não houver piloto; com o piloto futuro, fixação com ele);
   **L-04** eventual limiar próprio para CONSUMO/VENCIMENTO —
   pendência **P3.13-03** (hoje, pelas fontes, esses tipos exigem
   explicação e não reprovam sozinhos o critério monetário).
4. **Consequência de portão:** com L-01/L-02/L-03 em `[A DEFINIR]`,
   nenhum ciclo da LC.11 pode ser julgado "dentro do critério" — o
   roteiro (§3, pré-condição PC-4) exige a fixação registrada antes do
   1º ciclo. Rodar ciclo sem limiar e declará-lo aprovado seria falso.

## 3. Pré-condições (portão — qualquer "não" impede o início)

- **PC-1. LC.10 concluída** (dep declarada da LC.11): carga real do
  legado executada na produção, com conciliação assinada pelo cliente
  e relatório de recusas de XML aceito (pacote `lc.10/`).
- **PC-2. Homologação local de referência:** GL.2 executada e **GL.4
  decidido** (decisão formal registrada sobre GL.2/GL.3, com o
  relatório do paralelo local anexado como base deste paralelo real —
  aceite da GL.4). Estado hoje: GL.2 entregou o aparato, **sem ciclo
  real executado** (declaração da própria GL.2 e da P3.13 §0.4); GL.4
  em produção neste bloco, decisão formal pendente. Se na execução
  real a GL.2 tiver rodado apenas sobre a base fictícia, a base de
  comparação do §6 é essa — e o registo de divergências o declara
  (campo "base GL.2").
- **PC-3. Cliente real existente** com legado acessível (fator externo
  — hoje inexistente, item 34).
- **PC-4. Limiares L-01/L-02/L-03 fixados e registrados** (pendência
  P3.13-01 baixada), e perfil regulatório da agência do cliente
  (P2.31, RAS-01) ativo e com vigência conferida antes do 1º ciclo
  (critério C-04 da P3.13, por analogia obrigatória do aceite da
  GL.2); conteúdo mínimo da fatura (P2.32) conferido em cada conta
  (C-05).
- **PC-5. Trava fiscal armada** (§5): emissão fiscal de produção do
  ZiipSan desligada e conferida antes do 1º ciclo.
- **PC-6. Papéis nomeados:** operador do paralelo, conferente
  (distinto), representante do cliente. Nomes: `[A DEFINIR]` na
  execução — nenhum nome é presumido aqui.

## 4. Execução do ciclo (1 ou 2 ciclos — aceite)

Repetir por ciclo, no máximo **dois** (aceite da LC.11: "um ou dois
ciclos"). Ciclo = um ciclo completo de faturamento do cliente.

1. **Abertura do ciclo:** registrar ciclo nº, competência/período,
   escopo (unidade organizacional e áreas, P3.30/C-11) e a trava
   fiscal conferida (§5) no registo de divergências.
2. **Faturamento em paralelo:** o ZiipSan fatura o ciclo sobre o dado
   real carregado (LC.10) **na produção, em paralelo ao legado**. O
   **legado continua oficial e emissor** durante todo o paralelo
   (P3.13 §2.1 item 2; declaração da GL.4) — conta oficial do cliente é
   a do legado; a conta do ZiipSan é a conta comparada.
3. **Comparação conta a conta:** para cada conta, confrontar conta do
   ZiipSan × conta do legado: valor total, consumo, vencimento e
   presença (ausências nos dois sentidos). Usar os tipos e a
   disposição da P3.13 §1.2. O comparador da GL.2
   (`scripts/paralelo/comparar-contas.py`) é a referência de
   comportamento; na produção, a chave é a matrícula real (ver §2.2).
4. **Divergências:** cada divergência entra no registo
   (`MODELO-REGISTRO-DIVERGENCIAS-LC11.md`) com tipo, valores dos dois
   lados, **explicação obrigatória**, julgamento contra L-01/L-02/L-03
   e disposição. Sem explicação, a divergência reprova o ciclo (C-07);
   fora do limiar, reprova mesmo explicada (C-08).
5. **Retificação e conferência:** divergências corrigidas são
   retificadas pela P2.20 e conferidas pelos relatórios da P3.22
   dentro do próprio ciclo (C-09); devoluções e pagamentos não
   identificados do ciclo tratados pela P3.29 (C-10).
6. **Diferenças novas × GL.2:** para cada divergência, registrar se o
   tipo/causa/magnitude já constava da homologação local (GL.2) ou se
   é **diferença nova** (§6). Diferença nova não é escondida na massa
   das conhecidas — tem marcação própria no registo, por ser item
   expresso do aceite.
7. **Débito automático:** executar a conferência do §7 (só com
   optantes; sem optantes, registrar que a P3.24 não se aplica ao
   paralelo — ramo do aceite da GL.4/LC.11).
8. **Fechamento do ciclo:** veredito do ciclo = **dentro do critério e
   toda divergência explicada** → ciclo aprovado; qualquer outro
   estado → ciclo reprovado. Registrar o veredito e as somas
   (divergências por tipo, soma das diferenças absolutas × L-02,
   ausências × L-03) no registo.
9. **Segundo ciclo (se houver):** só após o fechamento registrado do
   1º. Esgotados 1–2 ciclos sem aprovação, **não se avança para o
   corte (LC.13)**: a decisão é de não cortar, registrada no termo de
   resultado (§8) — o legado segue oficial (volta por antecipação,
   P3.13 §2.3 item 1).

## 5. Trava — sem emissão fiscal de produção

Item de aceite da LC.11: "sem emissão fiscal de produção".

1. **Antes do 1º ciclo:** confirmar e registrar que a emissão fiscal
   do ZiipSan em ambiente de produção (`tpAmb=1`) está **desligada**
   para o tenant do paralelo. A virada fiscal é ato do corte (LC.13),
   nunca do paralelo.
2. **Durante os ciclos:** nenhuma NFAg-e de produção é emitida pelo
   ZiipSan. Se ensaio fiscal for necessário, é só no simulador ou na
   homologação da SEFAZ (`tpAmb=2`), com os XML assinados apagados ao
   fim do ciclo (regra C-06 da P3.13/GL.2) — fora da produção.
3. **Quem emite é o legado**, que continua oficial e emissor (§4.2).
4. **Violação da trava** (qualquer emissão `tpAmb=1` pelo ZiipSan
   durante o paralelo) = incidente registrado no registo de
   divergências como ocorrência própria, ciclo reprovado e paralelo
   suspenso até decisão formal registrada. Não se "corrige no
   silêncio".

## 6. Diferenças novas em relação à homologação local (GL.2)

Item de aceite da LC.11: "diferenças novas em relação à homologação
local (GL.2) registradas".

- **Base de comparação:** o resultado da GL.2 como estiver quando a
  LC.11 executar — ciclos reais da GL.2, se existirem, ou o ensaio
  sobre a base fictícia (modelo do item 34), declarado no registo.
  Se a GL.2 não tiver **nenhum** ciclo comparável, o campo registra
  expressamente `SEM BASE COMPARÁVEL — GL.2 sem ciclo real` — nunca
  se presume equivalência.
- **É diferença nova** a divergência cujo tipo, cuja causa-raiz ou
  cuja magnitude não aparece na base da GL.2. Cada uma é marcada
  `NOVA × GL.2` no registo, com a explicação e a disposição próprias.
- O termo de resultado (§8) totaliza as diferenças novas por ciclo —
  elas são insumo direto da decisão de corte (LC.13): diferença nova
  sem explicação ou fora do limiar tem o mesmo efeito de reprovação
  de qualquer divergência (§2, C-07/C-08).

## 7. Conferência do débito automático com optantes

Item de aceite da LC.11: "com optantes, débito automático conferido".
Cruzamento obrigatório: **decisão do dono, item 9 do
`DECISOES-DO-DONO.md`** (08/11/2026) — caminho duplo: **campanha de
conversão ao Pix Automático (P4.5)** E **FEBRABAN 150 mantido como
legado para a base atual de optantes; débito novo só por Pix
Automático**. (O parecer formal RAS-06/PD-10 continua inexistente como
documento — a decisão é do dono, não é parecer jurídico; declarado
para não se tomar a decisão por parecer.)

Pré-condição do ramo: existir optantes no cliente — existência a
levantar com o cliente real (quantidade e leiaute bancário são o
levantamento da P0.30 original; hoje `[SEM FONTE]` — nenhum número é
presumido). **Sem optantes:** registrar no termo de resultado que a
P3.24 não se aplica ao paralelo e encerrar este passo.

**Com optantes, conferir em cada ciclo:**

1. **Autorizações legadas:** as autorizações de débito automático
   importadas do legado (carga da P3.24/LC.10) conferidas **por
   matrícula** contra a base de optantes do legado: optante no legado
   sem autorização importada, ou autorização importada sem optante,
   é divergência do paralelo (tipo AUSÊNCIA no registo, com
   explicação) — nunca ajuste silencioso.
2. **Remessa FEBRABAN 150 (legado):** a remessa gerada pelo ZiipSan
   para os optantes legados (registros da P3.24) é confrontada, conta
   a conta, com a remessa do legado do mesmo ciclo — **sem envio
   duplicado ao banco**: quem envia e recebe no ciclo é o legado,
   sistema oficial durante o paralelo. Divergência de valor/optante
   entre as remessas entra no registo.
3. **Retorno:** o retorno real do legado (baixas do ciclo) é
   confrontado com o retorno esperado pelo ZiipSan: baixa divergente,
   rejeição ou optante sem baixa entram no registo com explicação.
4. **Débito novo no período:** nenhuma autorização nova entra pelo
   FEBRABAN 150 no ZiipSan (autorização nova é recusada na P3.24);
   adesão nova no ciclo é **Pix Automático (P4.5)** — conferir que
   nenhum optante novo do ciclo aparece na remessa legada do ZiipSan
   e que os novos constam do caminho Pix. Violação = divergência
   registrada com disposição.
5. **Datas do regime:** qualquer data de desligamento do débito
   legado é a deduzida da regra vigente do Banco Central pela P3.24 —
   **nunca digitada** neste roteiro nem no registo.

Resultado do passo (por ciclo): `conferido sem divergência` /
`conferido com divergências registradas e explicadas` / `não
conferido — motivo` — registrado no termo de resultado. "Conferido"
sem o confronto dos itens 1–4 não é conferência.

## 8. Fechamento do paralelo

1. Completar o registo de divergências dos ciclos executados
   (1 ou 2 — nem mais, nem menos que o executado).
2. Preencher e assinar o **termo de resultado do paralelo**
   (`MODELO-TERMO-RESULTADO-PARALELO-LC11.md`): veredito por ciclo,
   totais de divergências e de diferenças novas, resultado da
   conferência do débito automático, declaração da trava fiscal
   respeitada, e a conclusão — **apto a submeter à decisão de corte
   (LC.13)** ou **não apto**, com o motivo.
3. Registrar o resultado no diário de bordo do projeto.
4. O relatório do paralelo real é o insumo da decisão de corte
   (LC.13, P3.13 §2.2): **este roteiro não decide o corte** e nenhum
   resultado dele autoriza, por si, desligar o legado.

## 9. O que este roteiro não afirma

- Nenhum ciclo foi executado; nenhum número de divergência, soma ou
  veredito existe. Os documentos de registo e o termo são **modelos
  em branco**.
- A base fictícia de 12.000 UCs (item 34) **não** é o paralelo real:
  ela alimenta a homologação local (GL.2 no modelo vigente), não a
  LC.11, que exige dado real de cliente em produção.

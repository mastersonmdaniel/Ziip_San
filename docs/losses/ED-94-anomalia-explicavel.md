# ED-94 — Detecção de anomalia explicável em séries (OPE-36; ADR-0042)

Módulo `intelligence/ziipsan-losses`, pacote novo
`br.com.ziipsan.intelligence.losses.anomaly` (domínio puro, sem
JPA/entidades — padrão ED-85/ED-91/ED-92; persistência e adaptadores
são da integração). Faixa Flyway própria do §3 do plano: **V22_x**
(losses) — a linha da faixa nomeia expressamente "anomalia em séries"
entre ED-78/ED-85/ED-86/ED-94/ED-95. SQL em `db/pendente/` sem número.

## As séries (entradas, nunca recadastradas)

| Série | Fonte | Referência (`seriesRef`) |
|---|---|---|
| Vazão noturna por DMC | ED-85 (curva horária da ED-17 agregada nos macromedidores de entrada do DMC) | código do DMC |
| Pressão por ponto | ED-17 (`MeasuringPoint`/supervisório) | código do ponto de medição |
| Consumo por ligação | ED-17 (curva horária `TelemetryHourly`) | identificação da ligação |

A fiação monta `AnomalySeries` (referência, tipo, unidade declarada e
pontos ordenados) a partir dessas fontes; esta entrega não cria
cadastro de série nenhum.

## As regras (estatística simples e auditável — nenhum ML, nenhum modelo opaco)

1. **Desvio contra a mesma hora e o mesmo dia da semana**
   (`SAME_HOUR_WEEKDAY_DEVIATION`, as três séries): base = amostras
   anteriores da própria série no mesmo slot (dia da semana + HORA
   DO DIA — um alvo às 03:30 usa a base das 03:00; correção R4 da
   revisão cruzada); mediana e MAD (sigma robusto = 1,4826 × MAD,
   constante matemática publicada, não limiar); dispara com
   |valor − mediana| ≥ `deviationK` sigmas. A base NÃO tem teto nem
   paginação: entram todas as amostras anteriores do slot (sem perda
   silenciosa por truncamento). Sem `minBaselineSamples` amostras na
   base, o ponto não é julgado. MAD zero: qualquer diferença da
   mediana dispara, com o desvio reportado como ausente (não se
   disfarça divisão por zero).
2. **Consumo contínuo** (`CONTINUOUS_CONSUMPTION`, só consumo):
   corrida contígua (passo exato de 1h; buraco quebra) de ≥
   `continuousHours` horas com consumo > 0. Uma corrida máxima = um
   alerta, no último ponto da corrida.
3. **Consumo zero** (`ZERO_CONSUMPTION`, só consumo): idem, com
   consumo = 0 por ≥ `zeroHours` horas.
4. **Queda súbita de pressão** (`SUDDEN_PRESSURE_DROP`, só pressão):
   entre amostras consecutivas E ADJACENTES no tempo (passo exato de
   1h — o mesmo critério de contiguidade das corridas; correção R2 da
   revisão cruzada), queda relativa ≥ `pressureDropPercent`. Par
   espaçado (buraco na série, amostras a dias de distância) não é
   queda súbita: é ignorado. Anterior zero não define queda (par
   ignorado).

**Confiança (método interno declarado, não norma):** 0,5 no limiar
exato, subindo linearmente até 1,0 quando a grandeza dobra o limiar;
no desvio, multiplicada pela cobertura da base
`min(1, base / (2 × minBaselineSamples))`.

**Cada alerta mostra a série, a regra e a confiança:** `AnomalyAlert`
carrega o trecho da série que sustenta o alerta (`evidence`), a regra,
os números da comparação (observado, mediana, sigma, desvio), a
descrição do limiar usado, a explicação textual determinística, a
confiança e o **snapshot dos parâmetros** da avaliação (auditoria).

## Parâmetros (`anomaly.*`, sem nenhum padrão de fábrica)

- `anomaly.rules_enabled` — códigos das regras separados por vírgula,
  ou `ALL`. Ausente/vazio = `MissingParameterException`.
- `anomaly.sensitivity` — UM parâmetro só no aceite; o seu valor é
  um documento `chave=valor;...` que se DECOMPÕE nos CINCO limiares
  nomeados: `deviationK`, `minBaselineSamples`, `continuousHours`,
  `zeroHours`, `pressureDropPercent`. A decomposição é desta
  entrega; o formato é **proposto [A DEFINIR]** para confirmação na
  integração (P-ED94-03a; os valores por tenant são a P-ED94-03b).
  Campo ausente/desconhecido/inválido = erro explícito nomeando o
  campo. Cada componente tem faixa de SANIDADE validada (não é valor
  de domínio): `deviationK` ∈ (0, 20] sigmas robustos (acima disso a
  regra está desligada na prática — desligar é papel de
  `anomaly.rules_enabled`); `minBaselineSamples` ∈ [2, 520] (10 anos
  de slots semanais); `continuousHours`/`zeroHours` ∈ [2, 8760]
  (1 ano de horas); `pressureDropPercent` ∈ (0, 100] (é percentual;
  a fração vive em (0, 1]). Fora da faixa = recusa tipada nomeando
  campo, valor e faixa — nunca aceite em silêncio, nunca clamp.
  Nenhum limiar é constante de código apresentada como verdade de
  domínio.

## Fronteira dura do ADR-0042 (revisão humana)

- O `AnomalyDetector` devolve **só** alertas `PROPOSED` e não conhece
  nenhuma porta de ação: ele não pode abrir OS nem pesquisa.
- `ReviewedAlert` só nasce em `AnomalyReviewService.review(...)`, com
  revisor identificado, decisão (`CONFIRMED`/`DISMISSED`),
  justificativa obrigatória e instante ≥ detecção (construtor
  package-private de propósito: sem revisão registrada, o pedido de
  ação nem compila no chamador). Defesa em profundidade (correção R1
  da revisão cruzada): o construtor de `ReviewedAlert` REPETE as
  invariantes de `review(...)` (alerta PROPOSED; revisão não anterior
  à detecção) — nem código no mesmo pacote monta um `ReviewedAlert`
  que a revisão jamais emitiria.
- `executeFollowUp(...)` é o único caminho até a `FollowUpPort`
  (motor de OS da P1.31 / pesquisa de campo, fiação da integração):
  recusa descartado, recusa confirmação sem tipo de desfecho
  (`SERVICE_ORDER`/`FIELD_SURVEY` — confirmação sem tipo é ciência,
  não pedido) e exige a referência devolvida pela porta.
- No banco (SQL pendente): `UNIQUE (tenant_id, alert_id)` = uma
  revisão por alerta; o CHECK admite só as três formas finais da
  revisão (descartado: tipo e referência NULL; ciência: tipo e
  referência NULL; desfecho: tipo E referência preenchidos —
  correção R3 da revisão cruzada: não existe desfecho "pela metade",
  com tipo e sem referência).

## Teste com séries sintéticas de anomalia conhecida (OPE-36)

Suíte JUnit sobre séries construídas com a anomalia plantada e os
valores esperados calculados à mão: segundas-feiras 03:00 com base
[98,100,102,100] (mediana 100, MAD 1, sigma 1,4826) e salto para 110
(z = 6,7449; confiança 0,5; com base de 8, confiança 1,0); corridas de
24/30h contínuas e de 72/80h zeradas (confianças 0,5/0,625 e
0,5/0,5556); pressão 50→30 (queda de 40%; confiança 0,8); casos
negativos no limiar, abaixo dele, com buraco na série, com base
insuficiente e com regra desligada/inaplicável; e o portão de revisão
com porta-dublê contadora provando que descartado/sem-desfecho nunca
toca a ação.

## Fiação da integração (pendência nomeada)

Adaptadores que montam as três séries (ED-85/ED-17), agendamento da
avaliação, persistência JPA das duas tabelas, adaptador real da
`FollowUpPort` sobre o motor de OS da P1.31, tela de revisão (série +
regra + confiança) e numeração Flyway do SQL pendente (faixa V22_x).

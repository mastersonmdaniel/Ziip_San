# ADR-0029 — Capacidade e equipe para a trilha de editais

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0023 (item 4: equipe decidida no P4); ADR-0025 (item 3: plantão da severidade 1); ADR-0028 (volume
  da trilha); plano §2.4, §5 (M8, M11, M12, M13), §6 e trilha de editais; consolidado 18 §5.2 e §8
- **Base:** `docs/levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md` §5.2 e §8.1 (esforço recalculado na v2.5
  do plano); `18h-aderencia-nao-funcionais-servicos-habilitacao.md` §6 (decisão 5)
- **Ajuste de 12/10/2026 (editais, v2.6), antes de qualquer implementação:** com a ED-58 antecipada da onda C para a
  onda A (ADR-0032, ajuste v2.6), a prontidão de POC passa a cerca de 191 semanas-pessoa nas ondas A e B e cerca de
  199 com as tarefas originais do P4 (antes, 183 e 192); a necessidade para 30/06/2028 sobe de cerca de 4,5 para 4,6
  pessoas-equivalentes produtivas (para 31/12/2028, de 2,8 para 2,9), dentro da contratação de 4 a 5 do item 1, que não
  muda; com 5 contratações, a capacidade até 30/06/2028 (cerca de 225) fica cerca de 13% acima do esforço (antes,
  17%); com 4 (cerca de 186), cerca de 13 semanas-pessoa abaixo (antes, 6), com prontidão por volta do fim de julho de
  2028; com 2, no início do 2º trimestre de 2029; sem contratação, continua por volta de 2031; os números do Contexto,
  de "Sem a contratação" e de "Margem pequena" leem-se com esses valores; decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** "go-live do piloto (M10)" lê-se "homologação local
  do piloto (M10)" (ADR-0033), e a seleção de junho e julho de 2027 ocorre durante essa homologação; a entrada da
  equipe a partir de 02/08/2027 e a prontidão de POC (M12) não mudam; a primeira licitação com POC (M13) passa a
  depender do primeiro atestado, só possível depois do lançamento comercial, e o 2º semestre de 2028 só se mantém com a
  opção B do plano §2.4; o onboarding do item 4 continua só com homologação local e massa sintética, e acesso à
  produção só existe depois do lançamento; se o dono escolher lançar durante a trilha (opções B ou C), o lançamento
  ocupa cerca de 15 semanas-pessoa (±30%), em boa parte do dono do produto, o que absorve quase toda a folga de cerca de
  26 semanas-pessoa da contratação de 5 e aumenta o déficit da de 4; M12 e a necessidade de pessoas são recalculados com
  a escolha; decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.8), antes de qualquer implementação:** com a opção A do plano §2.4 (ADR-0033, ajuste
  v2.8), o lançamento comercial começa depois de todas as fases, inclusive a trilha completa, em outubro de 2028, e a
  meta do item 2 de disputar licitações com POC no 2º semestre de 2028 deixa de valer: a primeira licitação com POC
  (M13) passa a ser a partir do 3º trimestre de 2029, depois do primeiro atestado (maio a julho de 2029). A contratação
  do item 1 (4 a 5 desenvolvedores a partir de 02/08/2027) e a prontidão de POC em 30/06/2028 (M12) não mudam.
  **Análise de capacidade.** A prontidão em junho de 2028 continua coerente, por ser anterior ao lançamento, mas deixa
  de limitar a primeira licitação; o limite passa a ser a conclusão das ondas A, B e C e das tarefas originais do P4
  (cerca de 215 semanas-pessoa) até 30/09/2028. De 02/08/2027 a essa data há cerca de 56 semanas produtivas (43 até
  30/06/2028), e a necessidade cai para cerca de 3,8 pessoas-equivalentes (4,6 para a prontidão em junho). Com as
  premissas do Contexto (dono a 0,7, onboarding de 8 semanas a 50%), a capacidade até 30/09/2028 é de cerca de 299
  semanas-pessoa com 5 contratações (39% acima do esforço, acima da incerteza de ±30%; trilha completa no início do 3º
  trimestre de 2028), 247 com 4 (15% acima; trilha completa por volta do fim de agosto de 2028), 195 com 3 (9% abaixo;
  trilha completa no início de novembro de 2028, com lançamento, atestado e M13 cerca de um mês e meio depois) e 143
  com 2 (trilha completa por volta de abril de 2029 e primeira licitação em 2030). Com a equipe completa, o M12 pode
  escorregar até cerca do fim de agosto de 2028 sem mover o lançamento; atraso da trilha além de setembro de 2028
  desloca o lançamento, o atestado e o M13 na mesma medida, porque na opção A a trilha inteira fica no caminho até o
  atestado. A folga permite fixar a equipe em 4 no M8, limite inferior do item 1, ou adiar a entrada (até cerca de 3
  meses com 5 e de 1,5 mês com 4, com M12 por volta do início de setembro de 2028 e sem folga frente à incerteza);
  abaixo de 4 há déficit. A escolha fica como pendência do dono do produto no plano §2.4, até o M8 (28/05/2027), junto
  do número final do item 1. **Outros efeitos:** a consequência negativa "Custo fixo" lê-se com a primeira licitação a
  partir do 3º trimestre de 2029, cerca de dois anos depois da entrada, e a primeira receita de produção do piloto só
  depois do corte, por volta de fevereiro de 2029; a escala de plantão da S1 (item 5), montada até 31/01/2028, só é
  acionada na primeira produção; a alternativa "Equipe menor (duas pessoas)" deixa de perder as licitações de 2028,
  que a opção A já não disputa, mas leva a primeira licitação a 2030; decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.9), antes de qualquer implementação:** resolvida a pendência do ajuste v2.8 (plano
  §2.4). **Equipe.** O item 1 passa a **4 contratações**, com entrada a partir de 02/08/2027 e todas até 31/08/2027,
  nos perfis 1 a 4 do item 3; a confirmação do número no M8 deixa de existir, e a velocidade medida do P0 ao P3 serve só
  para recalibrar as estimativas. Com o lançamento em outubro de 2028 (opção A), a capacidade até 30/09/2028 é de cerca
  de 247 semanas-pessoa contra cerca de 215 da trilha completa: folga de cerca de 15%, dentro da incerteza de ±30%, que
  ela não cobre por inteiro; trilha completa por volta do fim de agosto de 2028. A meta do item 2 lê-se com a prontidão
  de POC (M12) por volta do fim de julho de 2028 (faixa de junho a agosto, pela incerteza), no lugar de 30/06/2028; o
  M12 pode escorregar até cerca do início de setembro de 2028 sem mover o lançamento. **Quinta contratação por
  gatilho.** Velocidade medida é a soma das estimativas (consolidado 18 §5) das tarefas da trilha aceitas no período,
  com tarefa G em andamento contada pela fração dos critérios de aceite cumpridos, dividida pela capacidade planejada
  do mesmo período com as premissas do Contexto (dono a 0,7, onboarding de 8 semanas a 50%). Ela é medida em
  01/11/2027, sobre o período de 02/08 a 31/10/2027 (capacidade planejada de cerca de 40 semanas-pessoa): (a) a partir
  de 90%, a equipe segue com 4; (b) abaixo de 90%, a quinta vaga é aberta em 01/11/2027, no perfil 5 do item 3 (QA e
  POC), salvo se a medição apontar outra frente como gargalo, com entrada até o fim de janeiro de 2028 e cerca de 28
  semanas-pessoa até 30/09/2028, o que mantém a trilha completa nessa data com velocidade de até 80%; (c) abaixo de
  80%, a quinta vaga é aberta e M12, M13 e M14 a M16 são registrados de novo no plano, porque nem a quinta pessoa
  garante 30/09/2028. A mesma regra é reaplicada ao fim de cada onda, com o efeito da quinta pessoa recalculado na data.
  O custo estimado da quinta vaga entra no plano de contratação do M8, como contingência (item 6). Com quatro, vale a
  segunda frase do item 3 (ensaios e matriz viva com o dono do produto) até a eventual quinta contratação. Ficam
  rejeitadas as opções de 5 contratações desde a entrada, de adiar a entrada e de contratar menos de 4. **Outros
  efeitos:** "Sem a contratação" (quatro contratações) e "Margem pequena" leem-se com estes valores; a escala de
  plantão da S1 (item 5) é montada entre os quatro; decisão do dono do produto.

## Contexto

O ADR-0023 abriu a trilha de editais no P4 e deixou a equipe para ser decidida no início dele. O consolidado 18,
recalculado com os ajustes do ADR-0028 e com os ADRs 0030 a 0032 (plano v2.5), estima a prontidão de POC (ondas A e B) em cerca de 183
semanas-pessoa, mais cerca de 8 das tarefas originais do P4: cerca de 192 semanas-pessoa a partir de 02/08/2027. Com
uma pessoa e agentes, a prontidão chega por volta de 2031. Para 30/06/2028 são precisas cerca de 4,5
pessoas-equivalentes produtivas desde agosto de 2027 (43 semanas produtivas); para 31/12/2028, cerca de 2,8.

Decidir a equipe só na abertura do P4 consumiria os primeiros meses da trilha em recrutamento. O nível Setor Público
(ADR-0025) exige solução da severidade 1 em 2 horas úteis, o que uma pessoa não sustenta.

## Decisão

1. **Contratação.** Contratar de **4 a 5 desenvolvedores**, com entrada **a partir de 02/08/2027**, depois do
   go-live do piloto (M10). Nenhuma contratação para a trilha antes do go-live: o caminho crítico do piloto segue com o
   dono do produto e agentes. O número (4 ou 5) é confirmado no M8 com a velocidade medida do P0 ao P3.
2. **Metas.** Prontidão de POC (M12) em **30/06/2028**, condicionada à entrada de toda a equipe até 31/08/2027;
   disputa de licitações com POC no **2º semestre de 2028** (M13), começando por editais do tipo SAMAE.
3. **Perfis sugeridos**, na ordem de contratação:

   | Ordem | Perfil | Foco na trilha |
   |---|---|---|
   | 1 | Backend Java sênior (Spring Boot, PostgreSQL, lote) | faturamento, arrecadação, cobrança, dívida ativa, contabilidade, e-Sfinge, integração com ERP (ED-22 a ED-34, ED-62) |
   | 2 | Frontend e mobile (React, TypeScript, Capacitor, Android nativo para plugins) | apps de campo e do cliente, impressão Zebra, portal (ED-18 a ED-20, ED-38 a ED-40) |
   | 3 | GIS e dados (PostGIS, MapLibre, GDAL, SQL analítico, Superset) | relatórios, BI, qualidade da água, GIS e migração do cadastro técnico (ED-46 a ED-55) |
   | 4 | Backend Java pleno | cadastro, hidrometria, OS, atendimento, omnichannel e chatbot (ED-12 a ED-17, ED-35 a ED-37, ED-41 a ED-43) |
   | 5 | QA e POC (Playwright, testes em aparelho, massa sintética) | roteiros, ensaios cronometrados, matriz viva, kit de campo, testes de equivalência (ED-01, ED-08) |

   Com quatro contratações, a quinta vaga não é aberta: ensaios e matriz viva ficam com o dono do produto, e cada
   desenvolvedor escreve os testes de ponta a ponta das próprias entregas. O dono do produto conta como cerca de 0,7
   pessoa-equivalente na trilha, por somar arquitetura, revisão, priorização, ensaios e habilitação.
4. **Onboarding** em 8 semanas, com produtividade estimada em 50% da escala do plano nesse período:
   - antes da entrada: conta individual com MFA, sem acesso à produção nem a dado real (só homologação e massa
     sintética); termo de confidencialidade e orientação de LGPD; regra de uso de agentes (nenhum dado real nem
     segredo enviado a agente);
   - semanas 1 e 2: leitura dirigida (plano, ADRs, arquitetura-alvo, RNF, consolidado 18), ambiente local pelos
     scripts do repositório e uma tarefa P em par com o dono;
   - semanas 3 a 8: tarefas M da onda A com revisão por par;
   - critério de saída: ao menos uma tarefa M aceita e participação em um ensaio de POC;
   - acesso à produção só depois de 90 dias e por necessidade (plantão), com papel mínimo, MFA e registro.
5. **Plantão.** Escala de plantão para a S1 (ADR-0025, item 3) entre os contratados, montada depois do onboarding e
   ativa até 31/01/2028 (ED-05). Nenhum contrato no nível Setor Público é assinado antes da escala ativa.
6. **Custo.** Não é fixado neste ADR. É item a estimar até o M8 (28/05/2027), com remuneração e encargos por perfil e
   regime (CLT ou PJ), recrutamento, equipamento, ferramentas de agentes por pessoa, kit de campo da POC e o efeito no
   preço por porte (ED-10). Orçamento e regime são decisão do dono do produto até o M8 (plano §2.4).
7. **Recrutamento.** Perfis, critérios e orçamento prontos no M8; seleção em junho e julho de 2027, durante o
   paralelo, sem tirar o dono do produto das tarefas do go-live além das entrevistas finais.

## Sem a contratação

Se a contratação não ocorrer ou ficar abaixo do previsto:

- **nenhuma contratação:** a prontidão de POC vai para cerca de 2031; M12 e M13 perdem a data; a venda continua por
  piloto, dispensa, CPSI e clientes que não exigem POC (ADR-0023, item 1); nenhum contrato no nível Setor Público;
- **duas contratações:** a prontidão vai para o 1º trimestre de 2029, e a primeira licitação com POC para 2029;
- **quatro contratações:** a prontidão fica por volta de meados de julho de 2028 (cerca de 6 semanas-pessoa abaixo
  da meta), dentro da incerteza de ±30% das estimativas;
- **entrada atrasada:** cada mês de atraso na entrada da equipe completa adia a prontidão em cerca de um mês.

Em qualquer desses casos as ondas seguem a ordem A → B → C, e as datas de M12 e M13 são registradas de novo no plano.

## Consequências

### Positivas

- A trilha ganha data alcançável para 2028 sem mexer no caminho crítico do piloto.
- O plantão da severidade 1 passa a ter equipe, o que destrava o nível Setor Público.
- A ordem de contratação segue a ordem das ondas: comercial e campo primeiro, GIS e BI em seguida.

### Negativas

- **Custo fixo** antes de haver receita de editais; a primeira licitação só ocorre no 2º semestre de 2028.
- **Risco de contratação:** mercado de Java sênior e de mobile concorrido, seleção de 6 a 10 semanas, rotatividade
  nos primeiros meses. Mitigação: recrutamento aberto em junho de 2027; período de experiência; documentação por ADR
  e diário; revisão por par entre contratados; como contingência, desenvolvimento por empresa contratada por entrega,
  com o código no repositório do Ziip e cessão dos direitos ao Ziip (os editais exigem autoria própria).
- **Gargalo no dono do produto** como revisor único; a revisão por par e os critérios de aceite das tarefas reduzem a
  dependência.
- **Margem pequena:** com 5 contratações, a capacidade até 30/06/2028 fica cerca de 17% acima do esforço estimado,
  menos que a incerteza de ±30%; com 4, fica abaixo. A recalibração a cada onda é obrigatória.
- Onboarding consome cerca de 4 semanas-pessoa por contratado e parte do tempo do dono do produto em agosto e setembro
  de 2027.

### O que muda no código

- Nada no produto. No repositório: guia de onboarding, regra de uso de agentes e convenções de revisão por par.

### O que muda no roadmap

- M8 passa a exigir plano de contratação (perfis, ordem, regime, custo estimado) e orçamento aprovados.
- M11 passa a ser a entrada da equipe; M12 fica em 30/06/2028 condicionado à entrada de todos até 31/08/2027.
- ED-05 (plantão) e ED-11 (rede de implantação) passam a depender desta equipe.

## Alternativas consideradas

- **Manter a decisão de equipe no início do P4 (ADR-0023, item 4).** Rejeitada: o recrutamento ocuparia os primeiros
  meses da trilha e empurraria a prontidão para 2029.
- **Contratar antes do go-live.** Rejeitada: tira o dono do produto do caminho crítico do piloto para recrutar e
  integrar pessoas no P2 e no P3, fases sem folga.
- **Só empresa terceirizada.** Rejeitada como caminho principal: dependência de terceiro no núcleo do produto e risco
  de contestação da autoria própria; mantida como contingência.
- **Equipe menor (duas pessoas) com prontidão em 2029.** Rejeitada: perde as licitações de 2028.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Plano de contratação: perfis, ordem, regime, custo estimado, orçamento | P3 (até o M8, 28/05/2027) |
| Guia de onboarding e regra de uso de agentes | P3 |
| Recrutamento e seleção | junho e julho de 2027 |
| Entrada e onboarding de 8 semanas | a partir de 02/08/2027 (M11) |
| Escala de plantão para a S1 (ED-05) | depois do onboarding, até 31/01/2028 |
| Recalibração do número de pessoas e das datas com a velocidade medida | M8 e fim de cada onda |

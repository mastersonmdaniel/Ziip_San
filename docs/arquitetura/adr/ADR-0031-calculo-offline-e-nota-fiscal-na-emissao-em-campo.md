# ADR-0031 — Cálculo offline e nota fiscal na emissão em campo

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0002 (versões de parâmetro gravadas no resultado); ADR-0003 (motor fiscal, contingência); ADR-0026
  (cálculo da conta no dispositivo; documento fiscal em campo); `docs/arquitetura/FISCAL-PORTE-JAVA.md` §3.4, §3.7 e
  §6; `docs/arquitetura/ARQUITETURA-ALVO.md` §2 (item 5); RNF-07; plano P1.13, P1.16, P2.5, P2.7, P2.20, ED-19
- **Base:** ADR-0026 (Consequências negativas, "Cálculo da conta no dispositivo" e "Documento fiscal em campo");
  `docs/levantamento/editais/18d-aderencia-cadastro-medicao-gis.md` §3 (E6); `18e-aderencia-faturamento-arrecadacao-cobranca.md`
  §3 (E6)

## Contexto

A emissão simultânea (VISAN, alínea e; SAMAE 2.11, crítico) imprime a conta no imóvel, logo depois da leitura, sem
rede garantida. A conta impressa precisa ser a mesma que o servidor calcularia: valor, itens e arredondamento. O cálculo
hoje está em motores Java puros, sem serviço (cascata de consumo, média, `TariffEngine`, tarifa social), e o
arredondamento ainda será unificado na P1.16. O app de campo é só Android (ADR-0026), e o Android executa Java.

A conta convive com a NFAg-e (ADR-0003). O manual e o XSD oficiais não foram publicados em versão final; não se sabe
se a contingência admite chave de acesso gerada fora do autorizador nem se exige XML assinado no momento da emissão
(`FISCAL-PORTE-JAVA.md` §4).

## Decisão

1. **Pacote da rota.** Ao carregar a rota, o servidor envia ao aparelho: uma **tarefa por ligação**, com `task_id`
   (UUIDv7) gerado no servidor; os dados da ligação usados no cálculo (categoria, economias, consumo mínimo, histórico
   para média e estimativa); as tabelas de tarifa e os parâmetros vigentes na competência, com o **identificador da
   versão** de cada um (ADR-0002, princípio 2); a versão do motor; o leiaute de impressão; os textos da conta; e, se o
   item 4 se aplicar, a faixa de numeração fiscal reservada ao aparelho. O pacote tem hash e validade (a janela do
   ciclo). Parâmetro alterado depois da carga, com vigência na competência, invalida o pacote, que é renovado na próxima
   conexão; o que já foi impresso com a versão anterior é tratado no item 6.
2. **Mesmo motor.** O núcleo de cálculo (cascata de consumo, média e estimativa, `TariffEngine`, tarifa social,
   tributos informativos e a regra de arredondamento da P1.16) fica num módulo Java puro, `ziipsan-billing-rules`, sem
   Spring, JPA nem E/S. O servidor e o **plugin nativo Android** do app de campo usam o **mesmo artefato binário** (mesma
   versão e mesmo hash); não há porte para outra linguagem. O módulo é compilado no nível de bytecode aceito pelo
   Android (Java 17, a conferir contra o Android Gradle Plugin vigente) e só usa APIs disponíveis no Android
   (`java.math`, `java.time`, coleções); o teste de fronteiras da P1.13 (ArchUnit) recusa dependência fora dessa lista.
   Porte para outra linguagem só por novo ADR, com os mesmos vetores.
3. **Equivalência provada no CI.**
   - Repositório de vetores comum, `billing-contract-fixtures`, versionado como o `fiscal-contract-fixtures`
     (`FISCAL-PORTE-JAVA.md` §5): cada caso traz a entrada (leituras, ligação, versões e valores dos parâmetros) e a
     saída esperada (consumo faturado, itens, total, arredondamento), a começar pelos casos da v1 e da P2.5.
   - Em todo build do módulo ou do app, os vetores rodam no JVM do servidor (JUnit) e no ART, em emulador Android
     (teste instrumentado). Qualquer diferença bloqueia o merge.
   - Teste diferencial noturno: uma amostra de 10 mil ligações da massa sintética da P2.9 calculada nos dois ambientes,
     com comparação item a item.
   - Divergência encontrada em produção vira vetor novo antes da correção.
4. **Conta impressa e NFAg-e.**
   - Se o leiaute oficial admitir emissão em contingência com chave de acesso gerada fora do autorizador, o app imprime
     a conta com a chave e o QR Code de contingência, numerados pela faixa reservada ao aparelho e à série. Toda faixa
     reservada tem destino registrado (autorizada, cancelada ou inutilizada), como em `FISCAL-PORTE-JAVA.md` §3.4.
   - O **certificado digital nunca vai ao aparelho**. Se o leiaute exigir XML assinado no momento da emissão, o app
     imprime a conta sem chave fiscal, com a indicação de que o documento fiscal será emitido depois da sincronização e
     ficará no portal, desde que o leiaute e a SEFAZ admitam essa forma; se não admitirem, a forma da emissão em campo
     volta ao dono do produto, por novo ADR, antes da ED-19.
   - **Dependência:** manual e XSD oficiais da NFAg-e (regime de contingência, chave, QR Code, prazo de transmissão),
     **a confirmar** (plano §2.4).
5. **Sincronização e recálculo.** Na sincronização, o app envia, por tarefa: leitura, ocorrência, fotos, conta
   calculada (itens, total, versões usadas, versão do motor), hash do conteúdo impresso, número e chave fiscal quando
   houver, e o registro das impressões. O servidor:
   - aplica **idempotência pela chave da tarefa**: `UNIQUE (task_id)` na conta de campo; reenvio devolve o mesmo
     resultado; tarefa reaberta gera tarefa nova, nunca segunda conta para a mesma tarefa;
   - recalcula com as mesmas versões e compara os valores depois do arredondamento;
   - se forem iguais, grava a conta como emitida em campo e publica na outbox a autorização da NFAg-e com os dados
     impressos, no prazo do leiaute;
   - se forem diferentes, segue o item 6.
6. **Divergência.**
   - A NFAg-e reflete o documento entregue ao consumidor: é autorizada com os valores impressos, dentro do prazo da
     contingência, e a correção vem depois, sempre como nova versão da conta (P2.20). A conta impressa nunca é alterada
     em silêncio.
   - A conta entra na **fila de divergências** do backoffice, com a diferença por item e a causa provável (versão de
     parâmetro desatualizada no pacote, versão do motor, cadastro alterado depois da carga).
   - **Quem aprova:** usuário do tenant com a permissão de retificação da P2.20, com aprovação dupla quando a diferença
     passar do limite do parâmetro de aprovação da P2.20. O Ziip não aprova correção de conta do tenant.
   - **Como corrige:** cobrança a maior na impressão é retificada logo, pela P2.20, com cancelamento ou substituição da
     NFAg-e conforme o leiaute, aviso ao consumidor e 2ª via no portal; cobrança a menor mantém o valor impresso no
     vencimento, e a diferença vai para conta posterior como item separado, com motivo e aviso, salvo regra diferente
     da ERI registrada em parâmetro.
   - Toda divergência abre registro de defeito no Ziip, porque os vetores deveriam tê-la impedido, e gera alerta na
     Central (taxa de divergência por ciclo acima de zero).
7. **Reimpressão e correção em campo.** Reimpressão repete o conteúdo da primeira impressão, marcada como 2ª via, sem
   recalcular. Corrigir uma leitura depois da impressão exige nova leitura com motivo, gravada como nova versão local;
   na sincronização ela segue o fluxo de retificação, com aprovação.

## Consequências

### Positivas

- Um só código de cálculo: não há porte a manter em paralelo, e a equivalência é provada a cada build.
- A conta emitida em campo grava as mesmas versões de parâmetro que a do lote, o que permite auditar e recalcular.
- Divergência tem dono, prazo e caminho de correção, sem mexer no documento já entregue.

### Negativas

- O módulo de regras fica preso ao nível de bytecode e às APIs do Android; recursos novos do Java só entram quando o
  Android os aceitar.
- O CI passa a precisar de emulador Android, com mais tempo e custo de build.
- A forma fiscal da emissão em campo depende de leiaute ainda não publicado; pode exigir novo ADR antes da ED-19.
- Mudança de parâmetro no meio do ciclo invalida pacotes e pode gerar divergências daquela competência.
- Faixas de numeração por aparelho exigem inutilização das sobras e controle de aparelho perdido (ADR-0030, `BLOCK`).

### O que muda no código

- Extração de `ziipsan-billing-rules` (módulo puro) a partir dos motores atuais, com regra no ArchUnit.
- Plugin nativo Android que chama o módulo; pacote de rota com versões; registro das impressões.
- `billing-contract-fixtures` e testes instrumentados no CI; teste diferencial noturno.
- Conta de campo com `task_id` único, recálculo, fila de divergências ligada à P2.20 e alerta na Central; tarefas de
  leitura da P2.7 (`V5_3`) com o `task_id`.
- No fiscal, faixa de numeração por aparelho e autorização posterior da NFAg-e em contingência, conforme o leiaute.

### O que muda no roadmap

- P1.16 (regra única de arredondamento) e P2.5 (motor do ciclo) passam a gerar os primeiros vetores comuns.
- ED-19 passa a incluir o módulo compartilhado, os vetores no CI, o pacote com versões e o fluxo de divergência (+2
  semanas, consolidado 18).

## Alternativas consideradas

- **Portar o motor para TypeScript e rodar na WebView.** Rejeitada: duas bases de código para a mesma regra fiscal e
  tarifária; a equivalência dependeria só dos vetores.
- **Compilar o Java para JavaScript ou WebAssembly.** Não adotada: ferramenta de build a mais sem necessidade, já que o
  app de campo é só Android; fica como opção se um dia houver campo em iOS.
- **Calcular só no servidor e imprimir depois.** Rejeitada: não há emissão simultânea, item crítico do SAMAE e
  pontuado na VISAN.
- **Certificado A1 no aparelho para assinar a NFAg-e em campo.** Rejeitada: chave privada do prestador em aparelho que
  circula em campo.
- **Corrigir a conta automaticamente no servidor quando divergir.** Rejeitada: o consumidor recebeu outro valor; toda
  correção precisa de versão, motivo e aprovação.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Regra única de arredondamento com os primeiros vetores comuns | P1 (P1.16) |
| Extração de `ziipsan-billing-rules` puro e regra de fronteira no ArchUnit | P2 (com a P2.5) |
| `billing-contract-fixtures` rodando no JVM; teste instrumentado no emulador e teste diferencial noturno | P4 (ED-19) |
| Pacote de rota com versões e faixa fiscal por aparelho; plugin Android do cálculo | P4 (ED-19) |
| Sincronização com `task_id`, recálculo e fila de divergências ligada à P2.20; alerta na Central | P4 (ED-19) |
| Conferir no manual e no XSD oficiais a contingência da NFAg-e e decidir a forma do item 4 | antes da ED-19 |

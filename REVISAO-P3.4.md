# Revisão cruzada — P3.4 (07/11/2026)

Feita nesta mesma frente (o agente está em profundidade 2/2, sem poder
acionar o subagente revisor do fluxo — mesmo registro das P3.5/P3.36).
Recomenda-se a revisão do agente principal antes do push. Conferência
regra a regra contra o acceptance, sobre o código escrito:

## Achados e correções aplicadas

1. **[MÉDIO] `JsonNode.asText(null)`** em `CutoffService` (handler do
   efeito de corte): a existência de `asText(String)` na API do Jackson 3
   não é certa; trocado por `hasNonNull ? asText() : null`, forma usada
   nos espelhos conferidos.
2. **[MÉDIO] Javadoc de `CutoffRevalidationRule`** apontava a constante
   de motivo de encerramento para `CutoffService`; a constante vive em
   `CutoffOrderTypes` — link corrigido.
3. **[BAIXO] `NOTICE_ALREADY_VALID` sem produtor**: o motivo existia no
   enum/SQL mas nenhum caminho o gravava. `CutoffService.issueNotice`
   passou a recusar aviso duplicado com o motivo registrado (aviso
   ISSUED ainda válido para a unidade).
4. **[BAIXO] Tag `<b>` malformada** no javadoc de `CutoffCalendar` —
   corrigida.

## Conferências que passaram

- **Ordem das camadas temporais**: federal é dia-granular (iniciar);
  SC é intervalo [sex 8h, seg 8h) e [8h do dia útil anterior, 8h do
  primeiro dia útil seguinte) ao redor de feriado E ponto facultativo.
  Traçados à mão: seg 16/11/2026 7h recusado / 8h liberado; qui 19/11 e
  sex 20/11/2026 bloqueados nas duas camadas; qua 25/11/2026 (ponto
  facultativo sintético) recusado só em SC.
- **Caso da v1**: 15/10/2026 + 30 dias = sáb 14/11 → primeira data
  seg 16/11/2026 ✔; ADASA 59/60/120/121 dias conferidos por contagem
  manual (17/09→60, 19/07→120, 18/07→121 em 16/11/2026) ✔.
- **Revalidação × máquina de estados da P1.31**: o cancelamento usa
  `closeNotExecuted`, válido de OPEN/SCHEDULED/IN_EXECUTION/
  WAITING_DEPENDENCY — ou seja, a revalidação roda ANTES de
  `engine.execute()` (é o desenho da ED-20: revalidar, depois executar).
  De EXECUTED o motor só fecha como executada; declarado no relatório.
- **Efeitos**: tipos com `effect_code` exigem handler registrado, senão
  o motor recusa a execução — o `ConnectionEffectPort` é registrado no
  construtor quando ligado; sem ligação, nada é marcado como aplicado.
- **JSON só pela classe `Json`** nas classes novas ✔ (grep conferido);
  nenhum `ObjectMapper` próprio.
- **SQL × enums**: todos os valores dos CHECKs da V9_2 existem nos
  enums Java e vice-versa (teste de consistência cobre).
- **Nenhuma asserção afrouxada**; nenhum teste de módulo existente foi
  tocado (pacote novo).

## Não provado aqui (vai para a CI)

- Compilação (sem JDK nesta VM) e o JDBC contra PostgreSQL.
- `setObject(LocalDate)`/`getObject(..., LocalDate.class)` do pgjdbc
  nas datas do aviso (padrão JDBC 4.2; o motor da P1.31 não usa datas
  locais, então não há precedente local — primeiro ponto a olhar se a
  rodada quebrar na P3.4).

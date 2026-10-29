# Instruções de redação das tarefas da v3.0

Para os agentes que redigem as tarefas do plano v3.0. Leia antes: `decisoes-do-dono-28-10-2026.md`,
`estrutura-v3-notas.md` e, para cada tarefa do seu lote, a entrada em `estrutura-v3.json` e as tarefas de origem em
`tarefas-v2.21.json` (campo `from`).

## Entrada por tarefa

- `estrutura-v3.json`: id, action, phase, wave, title, from, deps, size, findings_owned, notes_for_writer.
- `tarefas-v2.21.json`: title, origin, size, deps, acceptance (texto-base) e adjustments (ajustes v2.13 a v2.21).
- `mapa-de-donos.json`: para cada achado de que a tarefa é dona, o relatório de origem. Leia o achado no relatório
  (docs/levantamento/lacunas/22a a 22d; docs/levantamento/revisao-geral/23a a 23e) para extrair a proposta concreta.

## Saída

Um array JSON (UTF-8, LF, indentação de 2 espaços, sem U+FFFD nem CR), uma entrada por tarefa do lote, na ordem de
`estrutura-v3.json`, com os campos:

- `id`: igual à estrutura.
- `title`: o da estrutura (ajuste só a redação, sem mudar o sentido).
- `origin`: fontes curtas, separadas por "; ": a origem v2.21, os ADRs e os IDs dos achados de que é dona
  (por exemplo "17:X5; ADR-0039; CAD-68, DAD-07").
- `size`: o da estrutura (tarefas novas ou sem tamanho: "a estimar").
- `deps`: exatamente a lista da estrutura.
- `acceptance`: um único parágrafo, sem quebra de linha e sem o caractere "|", com critérios verificáveis separados
  por ponto e vírgula. Incorpore o texto-base, todos os ajustes com conteúdo e as propostas dos achados de que a
  tarefa é dona. Não use as etiquetas "Ajuste v2.xx"; preserve migrations, parâmetros, testes e fatos com fonte. Se
  um ponto de ajuste pertence agora a outra tarefa (notes_for_writer), não o repita: escreva "(ver P1.36)".
- `findings_owned`: igual à estrutura.
- `consumed`: lista curta do que foi incorporado: versões de ajuste usadas (por exemplo "v2.14, v2.15, v2.18") e
  achados atendidos; e o que foi deliberadamente deixado para outra tarefa.

## Regras

1. Nenhum conteúdo dos ajustes se perde: ou entra no aceite, ou é remetido com "(ver ID)".
2. Critério de aceite é verificável: diz o que existe, como se testa e qual regra vale. Evite listar títulos de
   achados (problema COE-08).
3. Sem linguagem de prazo para o projeto (ADR-0044): nada de "até", "prazo", "atraso" para o projeto. Datas de norma
   continuam (por exemplo "obrigatória a partir de 01/12/2026").
4. Sem gestão de equipe. Nomes técnicos em inglês; texto em português do Brasil, formal, sem marcas de texto gerado
   por IA.
5. Respeite as decisões do dono (ADR-0038, 0039, 0041, 0042, 0043, 0044 e o arquivo de decisões de 28/10/2026).
6. Não invente norma, número ou data: use o catálogo `docs/normas/catalogo-normas.json`.
7. Confira com Node: JSON válido, mesmo número de tarefas do lote, ids na ordem, nenhum "|" nem quebra de linha em
   `acceptance`, deps iguais às da estrutura.

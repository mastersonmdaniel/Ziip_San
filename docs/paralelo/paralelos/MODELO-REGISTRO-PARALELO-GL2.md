# Modelo de registro do paralelo local — GL.2 (ensaio de POC)

> **ISTO É UM MODELO.** Nenhum ciclo foi executado para produzir este
> arquivo; ele só mostra o formato que o orquestrador
> (`scripts/paralelo/paralelo-local.sh`) grava a cada execução real, para
> conferência antes da primeira execução. Valores entre `<...>` são
> campos que a execução preenche com o que mediu — nunca à mão.

- Carimbo: `<AAAA-MM-DDThh:mm:ss>`
- Resultado: **`<CONCLUIDO|FALHA|PARCIAL>`**
- Etapa da falha / causa: `<etapa — causa, quando FALHA>`
- Duração total medida: `<N>s`
- Config: `<caminho da config do paralelo>`
- Piloto (dir): `<diretório com ciclo-1/, ciclo-2/[, ciclo-3/]>`

## Etapas (nesta execução)

- `<pre-voo e, por ciclo: faturamento → conteúdo → escopo → comparação →
  retificação/relatórios (se houve divergência) → devoluções → fiscal,
  cada uma como o orquestrador a registrou>`

## Ciclos (resumo medido pelo comparador e pela limpeza fiscal)

- `ciclo <N>: <X> contas comparadas conta a conta, <Y> iguais,
  <Z> divergência(s) dentro do critério P0.30 e explicada(s), soma de
  diferenças <S> centavos, <A> ausência(s); conteúdo P2.32 conferido;
  devoluções/não identificados declarados: <O>; XML fiscais apagados ao
  fim do ciclo: <Q>`

> Registro gerado pela execução do orquestrador da GL.2 — é o "ensaio de
> POC registrado" do acceptance. Ele afirma somente o que a execução
> mediu/executou; ciclo não executado está marcado como tal. Não
> reescrever à mão para "melhorar" resultado: divergência é resultado do
> paralelo e fica registrada (relatório-comparacao-ciclo-N.json ao lado
> deste registro).

# Relatórios da restauração mensal (P4.7)

Esta pasta é o destino **versionável** dos relatórios gerados por
`scripts/monthly-restore.sh` (ver `docs/operacao/restauracao-mensal.md`):

- `<AAAA-MM>.md` — um relatório por mês, com resultado, duração total e por
  fase, origem do backup (com SHA-256 do dump), destino isolado e
  verificações. É **gerado pela rotina** na máquina do ambiente local; nenhum
  relatório é escrito à mão nem commitado sem uma execução real por trás.
- `ALERTA-RESTAURACAO-MENSAL.md` — só existe enquanto a última execução tiver
  falhado; a próxima execução com sucesso o apaga. Se este arquivo estiver
  aqui, a restauração mensal está devendo atenção.

Enquanto a rotina não rodar pela primeira vez no ambiente local, esta pasta
contém apenas este README — isso é o estado honesto, não uma falha.

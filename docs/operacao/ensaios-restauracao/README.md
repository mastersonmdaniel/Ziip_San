# Registros dos ensaios de restauração local (GL.3)

Esta pasta é o destino **versionável** dos registros gerados por
`scripts/operacao/ensaio-restauracao-local.sh` (ver
`docs/operacao/ROTEIRO-ENSAIO-RESTAURACAO-LOCAL-GL3.md`):

- `<AAAA-MM-DD-HHMMSS>-gl3/registro-ensaio.md` — um registro por execução do
  ensaio, com tempos medidos por etapa e no total (× referência de RTO de
  4 h, **sem meta** — a meta vale na LC.12), contagens, checksum, resultado
  do teste de RLS, restauração seletiva com a prova de não-afetação dos
  demais tenants e o desfazer do lote com a prova do efeito. É **gerado
  pelo orquestrador** na máquina do ambiente local; nenhum registro é
  escrito à mão nem commitado sem uma execução real por trás. Ao lado dele
  ficam os artefatos da execução (TSVs de contagem/checksum, teste de RLS,
  fotografias da testemunha e a saída do desfazer).
- `MODELO-REGISTRO-ENSAIO-GL3.md` — o modelo dos campos do registro (não é
  um ensaio executado).

Enquanto o ensaio não rodar pela primeira vez no ambiente local, esta pasta
contém apenas este README e o modelo — isso é o estado honesto, não uma
falha.

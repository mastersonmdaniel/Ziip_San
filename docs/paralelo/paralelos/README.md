# Registros do paralelo local (GL.2)

Cada execução do orquestrador `scripts/paralelo/paralelo-local.sh` grava
aqui um diretório `<carimbo>-gl2/` com:

- `registro-paralelo.md` — o **registro do ensaio de POC** do acceptance,
  preenchido com os valores medidos daquela execução (nunca editado à mão
  para "melhorar" resultado);
- `criterio.json` — o critério de divergência (fonte P0.30) extraído da
  config daquela execução;
- por ciclo: `relatorio-comparacao.json` (saída do comparador conta a
  conta), `limpeza-fiscal.json` (exclusão dos XML da P3.7: quantidade e
  hash do lote) e os logs dos hooks executados.

O arquivo `MODELO-REGISTRO-PARALELO-GL2.md`, nesta pasta, é só o modelo
de conferência do formato — **não é um ensaio executado**. Registro real
só nasce de execução real, no ambiente local completo (PC do dono).

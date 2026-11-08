package br.com.ziipsan.commercial.billing.waterquality;

import br.com.ziipsan.commercial.billing.parameters.StatementParameters;

import java.time.LocalDate;

/**
 * Um resultado mensal de qualidade da agua tal como o laboratorio o
 * reporta (P2.24, Decreto 5.440/2005): o parametro, o nome de exibicao, a
 * unidade, o resultado e a referencia (limite/padrao) — TODOS texto do
 * laboratorio, nunca valor presumido por esta tarefa. O plano nao fixa
 * catalogo de parametros nem valores de referencia, e nenhum e inventado
 * aqui: o bloco da conta imprime o que a fonte (planilha da P1.40 ou, no
 * futuro, o modulo da ED-50) entregou para aquele mes e sistema.
 *
 * @param referenceMonth competencia do resultado (primeiro dia do mes)
 * @param supplySystem   o sistema de abastecimento a que o resultado se
 *                       refere (chave textual do laboratorio/modulo; quem
 *                       resolve o sistema da unidade consumidora e o
 *                       chamador — cadastro/GIS —, nao esta tarefa)
 * @param parameterKey   chave estavel do parametro, como reportada
 * @param parameterName  nome de exibicao do parametro, como reportado
 * @param unit           unidade do resultado, como reportada (pode ser nula)
 * @param resultText     o resultado, como reportado (valor ou resultado qualitativo)
 * @param referenceText  a referencia/limite, como reportado (pode ser nulo)
 * @param source         a fonte do resultado (planilha ou modulo de qualidade)
 */
public record WaterQualityResult(
        LocalDate referenceMonth,
        String supplySystem,
        String parameterKey,
        String parameterName,
        String unit,
        String resultText,
        String referenceText,
        StatementParameters.WaterQualitySource source) {

    public WaterQualityResult {
        if (referenceMonth == null || referenceMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "referenceMonth deve ser o primeiro dia do mes: " + referenceMonth);
        }
        if (supplySystem == null || supplySystem.isBlank()) {
            throw new IllegalArgumentException("supplySystem e obrigatorio");
        }
        if (parameterKey == null || parameterKey.isBlank()) {
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        }
        if (parameterName == null || parameterName.isBlank()) {
            throw new IllegalArgumentException("parameterName e obrigatorio para " + parameterKey);
        }
        if (resultText == null || resultText.isBlank()) {
            throw new IllegalArgumentException(
                    "resultText e obrigatorio para " + parameterKey + ": resultado ausente nao entra no bloco");
        }
        if (source == null) {
            throw new IllegalArgumentException("source e obrigatoria para " + parameterKey);
        }
    }
}

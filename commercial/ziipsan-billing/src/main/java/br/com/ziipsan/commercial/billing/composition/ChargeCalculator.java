package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;

/**
 * Calculadora de UMA cobranca do catalogo, por modelo de calculo
 * (desenho da Camada 0, secao 2.1 item 1; Pilar 2 do ADR-0047/0048).
 *
 * <p>Contrato: {@link #model()} declara o modelo que a implementacao
 * atende (um dos seis de {@link CalculationModel}); {@link #calculate}
 * recebe o contexto com TUDO pre-resolvido pelo chamador — incluindo a
 * resolucao corrente ({@link ChargeCalculationContext#resolution()},
 * posta pelo maestro via
 * {@link ChargeCalculationContext#withResolution}) — e devolve a linha
 * calculada. A calculadora nao le banco, nao le catalogo (o consumo do
 * catalogo e SO pelo {@code ChargeResolver} da Camada 1, obrigacao
 * R1-02) e nao decide ordem de composicao: quem ordena e o
 * {@link BillComposer}.
 */
public interface ChargeCalculator {

    /** Modelo de calculo que esta calculadora implementa. */
    CalculationModel model();

    /**
     * Calcula a linha da cobranca resolvida que o contexto carrega como
     * resolucao corrente.
     *
     * @throws IllegalArgumentException se a resolucao corrente nao for
     *         do modelo desta calculadora ou se faltar entrada
     *         pre-resolvida exigida pelo modelo
     * @throws IllegalStateException se uma coerencia obrigatoria falhar
     *         (ex.: coerencia tarifa x cobranca, D-03)
     */
    BillChargeLine calculate(ChargeCalculationContext context);
}

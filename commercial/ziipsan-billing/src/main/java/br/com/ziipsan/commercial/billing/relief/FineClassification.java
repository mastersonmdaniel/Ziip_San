package br.com.ziipsan.commercial.billing.relief;

/**
 * Classificacao previa das multas/sancoes (NT 40/2026, item 6.6 — materia
 * precisada pelas fontes primarias sobre o F-07 do desenho):
 *
 * <ul>
 *   <li>multa <b>percentual</b> sobre tarifa/fatura: desonera automaticamente
 *       pela base — o percentual nao se transforma;</li>
 *   <li>multa <b>nominal de recuperacao de consumo</b> nao faturado: e receita
 *       de servico — segue a tarifa, aplica-se o factor (NT 6.6.2);</li>
 *   <li>multa <b>nominal indenizatoria</b>: exame proprio — NAO entra na
 *       geracao por presuncao;</li>
 *   <li>multa nominal <b>sem classificacao</b>: a geracao recusa (erro
 *       fechado). Factor unico sobre toda a tabela de sancoes produz
 *       resultado incorrecto (NT 6.6.2).</li>
 * </ul>
 */
public enum FineClassification {
    /** Percentual: sem transformacao do percentual; a base desonerada arrasta. */
    PERCENTUAL,
    /** Nominal, recuperacao de consumo (receita de servico): aplica o factor. */
    NOMINAL_RECOVERY_REVENUE,
    /** Nominal, indenizatoria: exame proprio; fora da geracao automatica. */
    NOMINAL_INDEMNITY,
    /** Nominal ainda nao classificada: geracao recusada. */
    NOMINAL_UNCLASSIFIED
}

package br.com.ziipsan.commercial.billing.special;

/**
 * Tipo de situacao especial de faturamento (ED-22; DAD-22, dicionario 23b
 * S3.17). Cada tipo carrega os <b>sete indicadores</b> que o acceptance
 * exige: eles descrevem o efeito do tipo e sao lidos pelas regras (nada e
 * inferido de texto livre).
 *
 * <ol>
 *   <li>{@code fixesWaterVolume} - fixa o volume de agua (consumo
 *       {@code SPECIAL_SITUATION_FIXED});</li>
 *   <li>{@code fixesSewerVolume} - fixa o volume de esgoto;</li>
 *   <li>{@code pausesBilling} - paralisa o faturamento (consumo calculado,
 *       nao faturado; semantica do {@code SpecialSituation} do metering);</li>
 *   <li>{@code exemptsCharges} - isenta a cobranca (agua/esgoto) na vigencia;</li>
 *   <li>{@code requiresLegalBasis} - exige base legal registrada;</li>
 *   <li>{@code requiresServiceOrder} - so entra/sai por OS
 *       ({@code SPECIAL_SITUATION_SET}/{@code SPECIAL_SITUATION_REMOVE});</li>
 *   <li>{@code allowsBatch} - pode ser aplicado em lote (OS-mae e OS-filhas).</li>
 * </ol>
 *
 * <p><b>Nota honesta de fonte:</b> o texto integral do 23b S3.17 nao esta
 * nesta VM; os sete indicadores foram modelados a partir do proprio
 * acceptance da ED-22 e do registro {@code SpecialSituation} ja existente
 * no metering (volumes fixos, pausa, vigencia). A conferencia nominal
 * contra o 23b fica registrada como pendencia de integracao no relatorio.
 *
 * <p><b>Risco explicito (correcao pos-revisao, D3):</b> na tabela atual,
 * {@code requiresServiceOrder=true} e {@code allowsBatch=true} nos
 * <b>quatro</b> tipos - ou seja, nenhum tipo real, como modelado, recusa
 * lote nem dispensa OS. Isso NAO foi "corrigido" inventando valores: sem o
 * 23b, nao ha fonte para dizer qual tipo recusaria. Em vez disso, os
 * indicadores sao <b>dados</b> ({@link Indicators}) e as regras os leem
 * como dados: o ramo de recusa em lote de {@code SpecialSituationService}
 * e alcancavel e esta provado por teste com um {@code Indicators}
 * sintetico (declarado no teste, fora desta tabela), sem falsificar a
 * tabela real. Se o 23b fixar valores diferentes para algum tipo, basta
 * trocar a linha da constante aqui - e o teste de recusa passa a valer
 * tambem para o tipo real.
 *
 * <p>A <b>isencao</b> e um tipo de situacao (nao um desvio por cliente):
 * {@link #EXEMPTION} tem {@code exemptsCharges} e
 * {@code requiresLegalBasis} ligados - sem base legal e sem vigencia o
 * registro e recusado (ver {@link SpecialSituationRecord}).
 */
public enum SpecialSituationType {

    /** Volume fixo de agua e/ou esgoto na vigencia. */
    FIXED_VOLUME(true, true, false, false, false, true, true),
    /** Faturamento paralisado na vigencia (ex.: obra, interdição). */
    BILLING_PAUSED(false, false, true, false, false, true, true),
    /** Isencao de cobranca, com base legal e vigencia obrigatorias. */
    EXEMPTION(false, false, false, true, true, true, true),
    /** Situacao generica sem efeito de volume/pausa/isencao (so registro e vigencia). */
    REGISTERED_ONLY(false, false, false, false, false, true, true);

    /**
     * Os sete indicadores como DADOS (correcao pos-revisao, D3): as regras
     * leem este valor, nao a identidade do tipo - por isso um conjunto
     * sintetico declarado em teste exercita os mesmos ramos de recusa que
     * um tipo real exercitaria se a sua linha na tabela mudasse no 23b.
     */
    public record Indicators(boolean fixesWaterVolume, boolean fixesSewerVolume, boolean pausesBilling,
                             boolean exemptsCharges, boolean requiresLegalBasis,
                             boolean requiresServiceOrder, boolean allowsBatch) {
    }

    private final Indicators indicators;

    SpecialSituationType(boolean fixesWaterVolume, boolean fixesSewerVolume, boolean pausesBilling,
                         boolean exemptsCharges, boolean requiresLegalBasis, boolean requiresServiceOrder,
                         boolean allowsBatch) {
        this.indicators = new Indicators(fixesWaterVolume, fixesSewerVolume, pausesBilling,
                exemptsCharges, requiresLegalBasis, requiresServiceOrder, allowsBatch);
    }

    /** Os sete indicadores deste tipo, como dados. */
    public Indicators asIndicators() { return indicators; }

    public boolean fixesWaterVolume() { return indicators.fixesWaterVolume(); }
    public boolean fixesSewerVolume() { return indicators.fixesSewerVolume(); }
    public boolean pausesBilling() { return indicators.pausesBilling(); }
    public boolean exemptsCharges() { return indicators.exemptsCharges(); }
    public boolean requiresLegalBasis() { return indicators.requiresLegalBasis(); }
    public boolean requiresServiceOrder() { return indicators.requiresServiceOrder(); }
    public boolean allowsBatch() { return indicators.allowsBatch(); }

    /** Os sete indicadores, na ordem do javadoc, para exibicao/relatorio. */
    public boolean[] indicators() {
        return new boolean[]{indicators.fixesWaterVolume(), indicators.fixesSewerVolume(),
                indicators.pausesBilling(), indicators.exemptsCharges(),
                indicators.requiresLegalBasis(), indicators.requiresServiceOrder(),
                indicators.allowsBatch()};
    }
}

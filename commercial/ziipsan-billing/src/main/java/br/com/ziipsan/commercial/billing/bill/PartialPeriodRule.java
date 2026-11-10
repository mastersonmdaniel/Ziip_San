package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billingrules.math.Money;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Período parcial e ciclo fora da faixa (JOR-11, RAS-14, P2.5): primeira conta, conta final e
 * ciclo com dias fora da faixa seguem o parâmetro do tenant {@code bill.partial_period_rule},
 * classe b, cujo padrão de fábrica é o <b>proporcional</b> — parte fixa e consumo
 * proporcionalizados pelos dias, na faixa de referência de 27 a 33 dias da
 * Res. ARESC 046/2016.
 *
 * <h2>O que "proporcionalizar o consumo" significa aqui</h2>
 * A parte fixa (a tarifa mínima, em reais) e a franquia de consumo (o consumo mínimo, em m³,
 * que a parte fixa remunera) são multiplicadas pelo fator {@code dias / dias de referência}.
 * O consumo <b>medido</b> de primeira conta e de conta final <b>não</b> é escalado: ele é o
 * consumo real daqueles dias, e o excedente cobrado nas faixas é
 * {@code medido − franquia proporcional} (nunca negativo). Escalar o medido cobraria por água
 * não consumida (para cima) ou perdoaria água consumida (para baixo). A interpretação está
 * registrada nas notas da frente F4 para conferência do coordenador.
 *
 * <h2>Ciclo regular fora da faixa é outro caso</h2>
 * Num ciclo regular (nem primeira nem última conta) fora da faixa, o que se ajusta é o consumo
 * medido, normalizado aos dias de referência pela média diária — o mesmo ajuste por dias que a
 * cascata de medição já faz ({@code ConsumptionCalculator}, com média diária de 4 casas
 * HALF_UP e resultado HALF_UP para inteiro). É o caso da leitura acima de 33 dias. A parte
 * fixa e a franquia acompanham o fator de dias, como no período parcial.
 *
 * <h2>Entradas da P1.3</h2>
 * A sazonalidade da unidade e a fonte alternativa de água (CAD-69, CAD-89) entram como
 * entradas declaradas do cálculo — as mesmas que a medição já representa em
 * {@code ConnectionCondition.seasonal()/hasWell()} — e são devolvidas no resultado para
 * auditoria e para as regras que as consomem. Nenhuma fonte lida atribui a elas efeito sobre
 * a proporcionalização, por isso elas <b>não</b> alteram os valores aqui; presumir um efeito
 * seria inventar regra (ver notas da frente F4).
 *
 * <h2>Arredondamento</h2>
 * Fator de dias: 4 casas, HALF_UP (o precedente do rateio por dias do {@code TariffEngine}).
 * Parte fixa proporcional: HALF_UP para centavos, via {@link Money}. Franquia e consumo
 * ajustado: HALF_UP para m³ inteiro. Nada usa ponto flutuante.
 */
public final class PartialPeriodRule {

    /** Menor ciclo dentro da faixa de referência (Res. ARESC 046/2016). */
    public static final int REFERENCE_BAND_MIN_DAYS = 27;
    /** Maior ciclo dentro da faixa de referência (Res. ARESC 046/2016). */
    public static final int REFERENCE_BAND_MAX_DAYS = 33;

    /** Chave do parâmetro do tenant que escolhe a regra de período parcial. */
    public static final String PARAMETER_KEY = "bill.partial_period_rule";

    private PartialPeriodRule() {
    }

    /** A regra de período parcial escolhida pelo tenant; o padrão de fábrica é o proporcional. */
    public enum Rule {
        /** Parte fixa e franquia de consumo proporcionalizadas pelos dias. */
        PROPORTIONAL;

        /**
         * Interpreta o valor vigente de {@code bill.partial_period_rule} (o JSON de
         * {@code parameter_versions.value}). A chave é classe b com padrão proporcional:
         * ausência de valor aceito vale o padrão — diferente das chaves sem padrão, cuja
         * ausência é erro. Conteúdo que não é JSON válido lança {@link IllegalStateException}
         * (da classe {@link Json} do core); nome fora do catálogo é recusado.
         */
        public static Rule fromParameterValue(String valueJson) {
            if (valueJson == null || valueJson.isBlank()) {
                return PROPORTIONAL;
            }
            JsonNode node = Json.readTree(valueJson);
            if (!node.isTextual()) {
                throw new IllegalArgumentException(
                        "O parâmetro " + PARAMETER_KEY + " precisa ser um texto com o nome da regra.");
            }
            try {
                return Rule.valueOf(node.asText().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("O parâmetro " + PARAMETER_KEY + " recebeu '"
                        + node.asText() + "', fora do catálogo (PROPORTIONAL).", e);
            }
        }
    }

    /** Qual período está sendo faturado. */
    public enum PeriodKind {
        /** Primeira conta da unidade (ligação dentro do ciclo): período parcial por construção. */
        FIRST_BILL,
        /** Conta final da unidade (desligamento dentro do ciclo): período parcial por construção. */
        FINAL_BILL,
        /** Ciclo comum, entre duas leituras: parcial só se os dias saírem da faixa 27–33. */
        REGULAR_CYCLE
    }

    /**
     * Entradas do ajuste, já resolvidas pelo chamador.
     *
     * @param kind                       primeira conta, conta final ou ciclo regular
     * @param periodDays                 dias do período faturado (entre as leituras, ou da ligação/desligamento)
     * @param referenceDays              dias do ciclo de referência do grupo/rota (o denominador da proporção)
     * @param fullFixedAmount            parte fixa do período completo (a tarifa mínima, em reais)
     * @param fullMinimumConsumption     franquia do período completo (o consumo mínimo, em m³)
     * @param measuredConsumption        consumo medido do período, em m³
     * @param seasonal                   imóvel sazonal (P1.3, CAD-89); entrada declarada, sem efeito no valor
     * @param hasAlternativeWaterSource  há fonte alternativa de água (poço, P1.3, CAD-69); idem
     */
    public record Input(PeriodKind kind, int periodDays, int referenceDays, BigDecimal fullFixedAmount,
                        int fullMinimumConsumption, int measuredConsumption,
                        boolean seasonal, boolean hasAlternativeWaterSource) {

        public Input {
            if (kind == null) {
                throw new IllegalArgumentException("kind é obrigatório");
            }
            if (periodDays < 1) {
                throw new IllegalArgumentException("periodDays deve ser >= 1: " + periodDays);
            }
            if (referenceDays < 1) {
                throw new IllegalArgumentException("referenceDays deve ser >= 1: " + referenceDays);
            }
            if (fullFixedAmount == null || fullFixedAmount.signum() < 0) {
                throw new IllegalArgumentException("fullFixedAmount é obrigatório e não pode ser negativo");
            }
            if (fullMinimumConsumption < 0) {
                throw new IllegalArgumentException("fullMinimumConsumption negativo: " + fullMinimumConsumption);
            }
            if (measuredConsumption < 0) {
                throw new IllegalArgumentException("measuredConsumption negativo: " + measuredConsumption);
            }
        }
    }

    /**
     * O período ajustado pela regra proporcional.
     *
     * @param partial             o período é parcial (primeira/final, ou ciclo regular fora da faixa)
     * @param withinReferenceBand os dias estão na faixa de 27–33 dias da Res. ARESC 046/2016
     * @param dayFactor           fator de dias aplicado ({@code dias / referência}, 4 casas); 1,0000 sem ajuste
     * @param fixedAmount         parte fixa a cobrar (proporcional, ou a cheia sem ajuste)
     * @param minimumConsumption  franquia a considerar (proporcional, ou a cheia sem ajuste)
     * @param billedConsumption   consumo que segue para as faixas: o medido (primeira/final e ciclo
     *                            na faixa) ou o medido normalizado aos dias de referência (ciclo fora da faixa)
     * @param excessConsumption   {@code max(0, billedConsumption − minimumConsumption)}: o que as faixas cobram
     * @param seasonal            a entrada de sazonalidade, devolvida para auditoria
     * @param hasAlternativeWaterSource a entrada de fonte alternativa, devolvida para auditoria
     */
    public record Result(boolean partial, boolean withinReferenceBand, BigDecimal dayFactor,
                         BigDecimal fixedAmount, int minimumConsumption, int billedConsumption,
                         int excessConsumption, boolean seasonal, boolean hasAlternativeWaterSource) {
    }

    /** Aplica a regra proporcional ao período. Sem ajuste, devolve os valores cheios e o medido. */
    public static Result adjust(Input in) {
        if (in == null) {
            throw new IllegalArgumentException("input é obrigatório");
        }
        boolean withinBand = in.periodDays() >= REFERENCE_BAND_MIN_DAYS
                && in.periodDays() <= REFERENCE_BAND_MAX_DAYS;
        boolean partial = in.kind() != PeriodKind.REGULAR_CYCLE || !withinBand;
        if (!partial) {
            return new Result(false, true, new BigDecimal("1.0000"), Money.roundHalfUp(in.fullFixedAmount()),
                    in.fullMinimumConsumption(), in.measuredConsumption(),
                    Math.max(0, in.measuredConsumption() - in.fullMinimumConsumption()),
                    in.seasonal(), in.hasAlternativeWaterSource());
        }

        BigDecimal factor = BigDecimal.valueOf(in.periodDays())
                .divide(BigDecimal.valueOf(in.referenceDays()), 4, RoundingMode.HALF_UP);
        BigDecimal fixed = Money.roundHalfUp(in.fullFixedAmount().multiply(factor));
        int minimum = BigDecimal.valueOf(in.fullMinimumConsumption()).multiply(factor)
                .setScale(0, RoundingMode.HALF_UP).intValueExact();

        int billed = in.measuredConsumption();
        if (in.kind() == PeriodKind.REGULAR_CYCLE) {
            // Ajuste por dias do ciclo regular fora da faixa (o da cascata): média diária com
            // 4 casas HALF_UP, vezes os dias de referência, HALF_UP para inteiro.
            BigDecimal daily = BigDecimal.valueOf(in.measuredConsumption())
                    .divide(BigDecimal.valueOf(in.periodDays()), 4, RoundingMode.HALF_UP);
            billed = daily.multiply(BigDecimal.valueOf(in.referenceDays()))
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
        }
        return new Result(true, withinBand, factor, fixed, minimum, billed,
                Math.max(0, billed - minimum), in.seasonal(), in.hasAlternativeWaterSource());
    }
}

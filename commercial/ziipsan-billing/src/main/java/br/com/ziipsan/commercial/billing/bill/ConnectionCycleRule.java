package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.parameters.BillParameterRules;
import br.com.ziipsan.commercial.billing.parameters.BillingParameters;

import java.math.BigDecimal;

/**
 * Leitura do ciclo a partir da LIGAÇÃO (ED-13): consumo mínimo e
 * situação vêm da ligação ({@code service_connections}/
 * {@code connection_statuses}, P2.23) — única fonte — e não mais da
 * projeção na unidade. Como todo o billing (ver {@code BillInput}), a
 * regra é pura: os fatos chegam resolvidos pelo chamador, que os lê da
 * ligação pelo serviço de fatos do cadastro
 * ({@code ConnectionCycleFactsService}) e os converte pela ponte
 * {@link ConnectionCycleFactsAdapter}.
 *
 * <p>Tipos UNIFICADOS com o serviço de fatos (correção A4 da revisão):
 * volumes são {@code BigDecimal} dos dois lados (a coluna do catálogo
 * é {@code NUMERIC(10,2)}) e {@code alternativeSourceVolumeM3} também —
 * a conversão para o volume inteiro do ciclo é explícita aqui dentro
 * (fração de m³ no mínimo ou na fonte alternativa é erro explícito,
 * nunca truncamento silencioso nem {@code ArithmeticException} crua).
 *
 * <p>Esgoto 'fora de uso': NÃO há mais decisão de bifurcação inventada
 * nesta regra (a chave {@code sewer.out_of_use_rule} da primeira
 * entrega não existia no catálogo e foi removida — correção B1). Quem
 * governa a cobrança é a PRÓPRIA situação da ligação de esgoto lida do
 * catálogo (flags {@code bills}/{@code minimum_*}): fora de uso por
 * cascata AUTOMATIC, a situação que a OS gravou no esgoto é a que o
 * ciclo lê; o fato {@code sewerOutOfUse} fica registrado nos fatos
 * para auditoria e para o chamador, sem atalho de cobrança paralelo.
 */
public final class ConnectionCycleRule {

    private ConnectionCycleRule() {
    }

    /**
     * Papel de sistema da situação da ligação — os MESMOS valores de
     * {@code ConnectionStatus.SystemRole} do cadastro (conversão por
     * nome no adaptador; enum local porque o billing não depende do
     * registry, como todo o desenho da P2.5).
     */
    public enum ConnectionRole { ACTIVE, CUT, SUSPENDED, DISCONNECTED, NO_CONNECTION, INACTIVE }

    /**
     * Fatos das ligações (água + esgoto combinados) para o ciclo.
     *
     * @param waterMinimumBillingConsumption mínimo de água da situação da ligação (m³), quando declarado
     * @param sewerMinimumBillingVolume volume mínimo de esgoto da situação (m³, NUMERIC(10,2)), quando declarado
     * @param hasAlternativeWaterSource imóvel com fonte alternativa vigente (P1.3: unit_alternative_sources)
     * @param alternativeSourceVolumeM3 volume captado da fonte alternativa, quando medido
     * @param sewerOutOfUse esgoto fora de uso no ciclo (fonte: bifurcação sewer.out_of_use_on_water_cut, no cadastro)
     */
    public record ConnectionFacts(
            ConnectionRole waterRole,
            boolean waterBills,
            boolean waterMinimumConsumption,
            Integer waterMinimumBillingConsumption,
            ConnectionRole sewerRole,
            boolean sewerBills,
            BigDecimal sewerMinimumBillingVolume,
            boolean hasAlternativeWaterSource,
            BigDecimal alternativeSourceVolumeM3,
            boolean sewerOutOfUse) {
    }

    /** Resultado da leitura do ciclo sobre a ligação. */
    public record CycleReading(
            Integer billedWaterConsumption,
            Integer sewerBaseVolume,
            boolean sewerBilled) {
    }

    public static CycleReading read(ConnectionFacts facts, BillingParameters parameters,
                                    int measuredWaterConsumption) {
        if (facts == null || parameters == null) {
            throw new IllegalArgumentException("Fatos da ligacao e parametros sao obrigatorios");
        }
        if (measuredWaterConsumption < 0) {
            throw new IllegalArgumentException("Consumo medido negativo: " + measuredWaterConsumption);
        }
        Integer billedWater = null;
        if (facts.waterRole() != null && facts.waterBills()) {
            billedWater = facts.waterMinimumConsumption()
                    ? Math.max(measuredWaterConsumption, requireMinimum(facts))
                    : measuredWaterConsumption;
        }
        boolean sewerBilled = facts.sewerRole() != null && facts.sewerBills();
        Integer sewerBase = null;
        if (sewerBilled) {
            sewerBase = baseFromWaterOrAlternative(facts, parameters, billedWater);
        }
        return new CycleReading(billedWater, sewerBase, sewerBilled);
    }

    /**
     * Volume-base do esgoto: o volume de água faturado; sem ele, a
     * regra {@code sewer.no_water_volume_rule} da P2.5 sobre a fonte
     * alternativa lida da ligação — é aqui que a fonte alternativa da
     * P1.3 entra na cobrança e sustenta a situação da ligação de
     * esgoto (imóvel com fonte alternativa tem esgoto cobrado pelo
     * volume da fonte, mesmo sem água da rede faturada).
     *
     * <p>O mínimo da ligação só é EXIGIDO no ramo que o usa
     * ({@code MINIMUM_VOLUME}): com {@code ALTERNATIVE_SOURCE_VOLUME}
     * ou {@code NO_SEWER_CHARGE}, situação de esgoto sem mínimo
     * declarado no catálogo não é erro (correção A3 da revisão).
     */
    private static int baseFromWaterOrAlternative(ConnectionFacts facts, BillingParameters parameters,
                                                  Integer billedWater) {
        if (billedWater != null) {
            return billedWater;
        }
        int alternative = facts.hasAlternativeWaterSource() && facts.alternativeSourceVolumeM3() != null
                ? wholeVolume(facts.alternativeSourceVolumeM3(), "volume da fonte alternativa")
                : 0;
        int minimum = parameters.noWaterVolumeRule() == BillingParameters.NoWaterVolumeRule.MINIMUM_VOLUME
                ? requireSewerMinimum(facts) : 0;
        return BillParameterRules.sewerBaseVolumeWithoutWater(parameters, alternative, minimum);
    }

    private static int requireMinimum(ConnectionFacts facts) {
        if (facts.waterMinimumBillingConsumption() == null) {
            throw new IllegalStateException("Situacao da ligacao de agua marca consumo minimo "
                    + "sem minimum_billing_consumption declarado no catalogo.");
        }
        return facts.waterMinimumBillingConsumption();
    }

    private static int requireSewerMinimum(ConnectionFacts facts) {
        if (facts.sewerMinimumBillingVolume() == null) {
            throw new IllegalStateException("Situacao da ligacao de esgoto sem "
                    + "minimum_billing_volume declarado no catalogo.");
        }
        return wholeVolume(facts.sewerMinimumBillingVolume(), "minimum_billing_volume da ligacao de esgoto");
    }

    /**
     * Volume do catálogo/fonte (NUMERIC) -> volume inteiro do ciclo.
     * Fração é erro EXPLÍCITO no padrão dos demais erros da regra —
     * a coluna admite centésimos, mas o ciclo fatura m³ inteiros e
     * arredondar em silêncio inventaria volume.
     */
    static int wholeVolume(BigDecimal volume, String label) {
        try {
            return volume.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Volume fracionario em " + label + ": " + volume
                    + " — o ciclo fatura volumes inteiros de m3 e nao arredonda em silencio.");
        }
    }
}

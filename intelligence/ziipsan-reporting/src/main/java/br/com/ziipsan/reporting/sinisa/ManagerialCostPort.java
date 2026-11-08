package br.com.ziipsan.reporting.sinisa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura dos indicadores gerenciais CALCULADOS pela ED-32
 * (GES-09): custo por m3, custo por ligacao, margem por categoria e custo
 * total da OS. O aceite da ED-48 manda LER esses indicadores da ED-32 —
 * as formulas (rateio, custo-hora com vigencia, margem) sao da
 * {@code ManagerialCosts} da ED-32 e NAO sao reimplementadas aqui.
 *
 * <p>Contrato de ausencia: {@link Optional#empty()} / lista vazia significam
 * "a ED-32 nao tem o dado neste periodo" — o painel mostra a pendencia
 * nomeada, nunca zero presumido. A fiacao (adaptador sobre a ED-32
 * integrada) e da integracao: o modulo reporting nao depende do modulo
 * accounting, como o reporting tambem nao depende do integration da P4.2.
 */
public interface ManagerialCostPort {

    /** Custo por m3 faturado do periodo (ED-32: custo total / m3 faturado). */
    Optional<BigDecimal> costPerCubicMeter(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    /** Custo por ligacao do periodo (ED-32: custo total / ligacoes). */
    Optional<BigDecimal> costPerConnection(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    /** Margem por categoria do periodo (ED-32: (receita - custo) / receita, por categoria). */
    List<CategoryMargin> marginsByCategory(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    /** Custo total de UMA ordem de servico (material + mao de obra + frota + terceiros, ED-32). */
    Optional<BigDecimal> serviceOrderTotalCost(UUID tenantId, String serviceOrderCode);

    /** Margem de uma categoria, calculada pela ED-32. */
    record CategoryMargin(String category, BigDecimal margin) {
    }
}

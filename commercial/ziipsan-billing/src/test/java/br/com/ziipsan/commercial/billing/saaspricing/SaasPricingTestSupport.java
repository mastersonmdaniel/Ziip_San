package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Fixtures de TESTE: valores arbitrarios de prova aritmetica, nao sao tabela comercial. */
final class SaasPricingTestSupport {
    private SaasPricingTestSupport() {}

    static PriceSheet sheet() {
        return new PriceSheet("TESTE-1", LocalDate.of(2026, 1, 1), null,
                List.of(new SizeBand("P", 0, 1000), new SizeBand("M", 1001, 5000), new SizeBand("G", 5001, 999999999)),
                List.of(
                        new CostComponent("hospedagem-base", new BigDecimal("1000.00"), CostComponent.Allocation.FIXED_PER_TENANT),
                        new CostComponent("hospedagem-por-ligacao", new BigDecimal("2.00"), CostComponent.Allocation.PER_CONNECTION),
                        new CostComponent("plataforma-compartilhada", new BigDecimal("10000.00"), CostComponent.Allocation.SHARED_POOL)),
                List.of(new ProfessionalProfile("suporte", new BigDecimal("100.00"), new BigDecimal("10"))));
    }

    static SaasPricingParameters params() {
        return SaasPricingParameters.fromValues(Map.of(
                "saas.margin_percent", "25",
                "saas.reversion_discount_percent", "10",
                "saas.pass_through_mode", "FRANCHISE"));
    }
}

package br.com.sigsan.commercial.billing.fixture;

import br.com.sigsan.commercial.billing.tariff.CategoryRule;
import br.com.sigsan.commercial.billing.tariff.DiscountBand;
import br.com.sigsan.commercial.billing.tariff.TariffGroup;
import br.com.sigsan.commercial.billing.tariff.SocialTariffRule;
import br.com.sigsan.commercial.billing.tariff.TariffBlock;
import br.com.sigsan.commercial.billing.tariff.TariffSchedule;
import br.com.sigsan.commercial.billing.tariff.TariffVersion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tabelas de tarifa <b>FICTÍCIAS</b>, só para os testes. Nenhum destes valores é tarifa real de
 * prestador nenhum; o produto não tem tarifa embutida (a tarifa é política do tenant).
 *
 * <p>T1 (relatório 02, seção 10):
 * <pre>
 *   RES: mínimo 10 m³, tarifa mínima 30,00;  até 20 → 4,00;  até 50 → 6,00;  acima → 9,00
 *   COM: mínimo 10 m³, tarifa mínima 50,00;  até 20 → 7,00;  até 50 → 10,00; acima → 14,00
 * </pre>
 * A faixa final é aberta (o relatório usa 999999 como "infinito").
 */
public final class FictitiousTariffs {

    public static final UUID RES = id(1);
    public static final UUID COM = id(2);
    public static final UUID SUB_R1 = id(11);

    public static final LocalDate T1_FROM = LocalDate.of(2026, 1, 1);

    private FictitiousTariffs() {
    }

    public static UUID id(int n) {
        return new UUID(0L, n);
    }

    public static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    public static TariffBlock block(Integer to, String price) {
        return new TariffBlock(to, d(price));
    }

    public static CategoryRule res(int min, String charge, TariffBlock... blocks) {
        return CategoryRule.forCategory(RES, min, d(charge), null, List.of(blocks));
    }

    public static CategoryRule com(int min, String charge, TariffBlock... blocks) {
        return CategoryRule.forCategory(COM, min, d(charge), null, List.of(blocks));
    }

    public static CategoryRule t1Res() {
        return res(10, "30.00", block(20, "4.00"), block(50, "6.00"), block(null, "9.00"));
    }

    public static CategoryRule t1Com() {
        return com(10, "50.00", block(20, "7.00"), block(50, "10.00"), block(null, "14.00"));
    }

    /** Versão T1 (RES e COM), vigente a partir de 2026-01-01, aberta. */
    public static TariffVersion t1() {
        return new TariffVersion(id(100), T1_FROM, null, List.of(t1Res(), t1Com()), null);
    }

    public static TariffVersion t1WithSocial(SocialTariffRule social) {
        return new TariffVersion(id(100), T1_FROM, null, List.of(t1Res(), t1Com()), social);
    }

    public static TariffSchedule scheduleOf(TariffVersion... versions) {
        return new TariffSchedule(List.of(versions));
    }

    public static TariffSchedule t1Schedule() {
        return scheduleOf(t1());
    }

    public static SocialTariffRule social(String minChargePct, DiscountBand... bands) {
        return new SocialTariffRule(d(minChargePct), List.of(bands));
    }

    public static DiscountBand band(int from, Integer to, String pct) {
        return new DiscountBand(from, to, d(pct));
    }

    public static TariffGroup res(int economies) {
        return TariffGroup.of(RES, economies);
    }

    public static TariffGroup com(int economies) {
        return TariffGroup.of(COM, economies);
    }

    public static TariffGroup socialRes(int economies, int social) {
        return new TariffGroup(RES, null, economies, social);
    }
}

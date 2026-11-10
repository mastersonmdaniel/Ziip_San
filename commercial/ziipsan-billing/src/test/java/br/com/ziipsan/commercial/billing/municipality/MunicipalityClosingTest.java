package br.com.ziipsan.commercial.billing.municipality;

import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.BillLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Closing;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Consolidated;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.ConsumptionLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.MunicipalityParcel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-04 — Fecho por município + consolidado sobre fixtures em memória.
 * Os IBGEs são os códigos FICTÍCIOS da base fictícia 12k (9990001/9990002,
 * lidos do seed); os valores das fixtures pequenas são aritmética deste
 * teste. A fixture das âncoras reproduz os TOTAIS provados na base 12k
 * (8.475/2.825 contas; R$ 914.529,00/R$ 306.502,00; tenant 11.300 /
 * R$ 1.221.031,00) gerando as linhas programaticamente — a prova desses
 * números contra o seed é a diferencial PG, não este teste.
 */
class MunicipalityClosingTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String IBGE_A = "9990001"; // fictício (seed base-ficticia-12k)
    private static final String IBGE_B = "9990002"; // fictício (seed base-ficticia-12k)
    private static final LocalDate REF = LocalDate.of(2026, 10, 1);

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    private static BillLine bill(int n, String ibge, String total) {
        BigDecimal t = new BigDecimal(total);
        // water = total, sewer/debits/credits/taxes = 0: decomposição válida
        // pela CHECK da V4_1 (total = agua+esgoto+debitos-creditos-impostos).
        return new BillLine(TENANT, id(100_000 + n), id(200_000 + n), REF, ibge, t, t, new BigDecimal("0.00"));
    }

    private static ConsumptionLine consumption(int n, String ibge, int billed, int calculated) {
        return new ConsumptionLine(TENANT, id(300_000 + n), id(200_000 + n), REF, "WATER", ibge, billed, calculated);
    }

    @Test
    void parcelasPorMunicipioSomamContasEValores() {
        Closing c = MunicipalityClosing.close(TENANT,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00"), bill(3, IBGE_B, "50.00")),
                List.of(consumption(1, IBGE_A, 10, 11), consumption(2, IBGE_B, 12, 12)));

        MunicipalityParcel a = c.parcelFor(IBGE_A).orElseThrow();
        assertEquals(2, a.billCount());
        assertEquals(new BigDecimal("300.00"), a.billsTotal());
        assertEquals(1, a.consumptionRecordCount());
        assertEquals(10, a.billedConsumption());
        assertEquals(11, a.calculatedConsumption());

        MunicipalityParcel b = c.parcelFor(IBGE_B).orElseThrow();
        assertEquals(1, b.billCount());
        assertEquals(new BigDecimal("50.00"), b.billsTotal());
        assertEquals(12, b.billedConsumption());
    }

    @Test
    void consolidadoEASomaDasParcelasPorConstrucao() {
        List<BillLine> bills = List.of(
                bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00"),
                bill(3, IBGE_B, "50.00"), bill(4, null, "7.50"));
        List<ConsumptionLine> cons = List.of(
                consumption(1, IBGE_A, 10, 11), consumption(2, IBGE_B, 12, 12),
                consumption(3, null, 1, 1));
        Closing c = MunicipalityClosing.close(TENANT, bills, cons);

        long parcelsBillCount = c.parcels().stream().mapToLong(MunicipalityParcel::billCount).sum();
        BigDecimal parcelsTotal = c.parcels().stream().map(MunicipalityParcel::billsTotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        long parcelsBilled = c.parcels().stream().mapToLong(MunicipalityParcel::billedConsumption).sum();

        Consolidated consTenant = c.consolidated();
        assertEquals(parcelsBillCount, consTenant.billCount());
        assertEquals(0, parcelsTotal.compareTo(consTenant.billsTotal()));
        assertEquals(parcelsBilled, consTenant.billedConsumption());
        // E o consolidado é o total directo das linhas (invariante do tenant):
        assertEquals(4, consTenant.billCount());
        assertEquals(new BigDecimal("357.50"), consTenant.billsTotal());
        assertEquals(23, consTenant.billedConsumption());
    }

    @Test
    void ancorasDaBase12kReproduzidasComoFixture() {
        // Gera 8.475 contas somando 914.529,00 e 2.825 somando 306.502,00:
        // (n-1) contas de 100,00 e a última com o resto — aritmética exacta.
        List<BillLine> bills = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < 8474; i++) {
            bills.add(bill(n++, IBGE_A, "100.00"));
        }
        bills.add(bill(n++, IBGE_A, "67129.00")); // 847400 + 67129 = 914529
        for (int i = 0; i < 2824; i++) {
            bills.add(bill(n++, IBGE_B, "100.00"));
        }
        bills.add(bill(n++, IBGE_B, "24102.00")); // 282400 + 24102 = 306502

        Closing c = MunicipalityClosing.close(TENANT, bills, List.of());
        assertEquals(8475, c.parcelFor(IBGE_A).orElseThrow().billCount());
        assertEquals(new BigDecimal("914529.00"), c.parcelFor(IBGE_A).orElseThrow().billsTotal());
        assertEquals(2825, c.parcelFor(IBGE_B).orElseThrow().billCount());
        assertEquals(new BigDecimal("306502.00"), c.parcelFor(IBGE_B).orElseThrow().billsTotal());
        assertEquals(11300, c.consolidated().billCount());
        assertEquals(new BigDecimal("1221031.00"), c.consolidated().billsTotal());
    }

    @Test
    void municipioUnicoEoComportamentoConsolidadoDeHoje() {
        // Tenant mono-município: a única parcela É o consolidado, campo a campo —
        // o fecho de hoje (CycleService sobre o tenant inteiro) não muda.
        Closing c = MunicipalityClosing.close(TENANT,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00")),
                List.of(consumption(1, IBGE_A, 10, 11)));

        assertEquals(1, c.parcels().size());
        MunicipalityParcel only = c.parcels().get(0);
        Consolidated t = c.consolidated();
        assertEquals(only.billCount(), t.billCount());
        assertEquals(only.billsTotal(), t.billsTotal());
        assertEquals(only.waterValue(), t.waterValue());
        assertEquals(only.sewerValue(), t.sewerValue());
        assertEquals(only.consumptionRecordCount(), t.consumptionRecordCount());
        assertEquals(only.billedConsumption(), t.billedConsumption());
        assertEquals(only.calculatedConsumption(), t.calculatedConsumption());
        assertTrue(c.unassignedParcel().isEmpty());
    }

    @Test
    void linhasSemDerivacaoNaoSomemFormamParcelaPropria() {
        Closing c = MunicipalityClosing.close(TENANT,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, null, "40.00")),
                List.of(consumption(1, null, 5, 5)));

        MunicipalityParcel un = c.unassignedParcel().orElseThrow();
        assertEquals(1, un.billCount());
        assertEquals(new BigDecimal("40.00"), un.billsTotal());
        assertEquals(5, un.billedConsumption());
        // A parcela sem derivação entra no consolidado: total do tenant intacto.
        assertEquals(2, c.consolidated().billCount());
        assertEquals(new BigDecimal("140.00"), c.consolidated().billsTotal());
        // E ordena por último (auditável: nunca se mistura com um IBGE).
        assertTrue(c.parcels().get(c.parcels().size() - 1).unassigned());
    }

    @Test
    void fechoPorReferenciaIgnoraOutrosMeses() {
        LocalDate other = LocalDate.of(2026, 9, 1);
        BillLine inMonth = bill(1, IBGE_A, "100.00");
        BillLine outMonth = new BillLine(TENANT, id(900_001), id(200_001), other, IBGE_A,
                new BigDecimal("999.00"), new BigDecimal("999.00"), new BigDecimal("0.00"));
        ConsumptionLine outCons = new ConsumptionLine(TENANT, id(900_002), id(200_001), other,
                "WATER", IBGE_A, 99, 99);

        Closing c = MunicipalityClosing.closeReference(TENANT, REF,
                List.of(inMonth, outMonth), List.of(outCons));
        assertEquals(REF, c.referenceMonth());
        assertEquals(1, c.consolidated().billCount());
        assertEquals(new BigDecimal("100.00"), c.consolidated().billsTotal());
        assertEquals(0, c.consolidated().consumptionRecordCount());
    }

    @Test
    void fechoVazioEZeroSemParcelas() {
        Closing c = MunicipalityClosing.close(TENANT, List.of(), List.of());
        assertTrue(c.parcels().isEmpty());
        assertEquals(0, c.consolidated().billCount());
        assertEquals(new BigDecimal("0.00"), c.consolidated().billsTotal());
        assertEquals(0, c.consolidated().billedConsumption());
    }

    @Test
    void linhaDeOutroTenantEerroExplicito() {
        UUID other = UUID.fromString("22222222-3333-4444-8555-666666666666");
        BillLine foreign = new BillLine(other, id(1), id(2), REF, IBGE_A,
                new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("0.00"));
        assertThrows(IllegalArgumentException.class,
                () -> MunicipalityClosing.close(TENANT, List.of(foreign), List.of()));
    }

    @Test
    void consumoNegativoRecusadoComoNoDominio() {
        assertThrows(IllegalArgumentException.class, () -> consumption(1, IBGE_A, -1, 0));
    }
}

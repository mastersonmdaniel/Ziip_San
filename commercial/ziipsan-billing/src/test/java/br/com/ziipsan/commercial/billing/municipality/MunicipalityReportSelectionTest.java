package br.com.ziipsan.commercial.billing.municipality;

import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.BillLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Closing;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.ConsumptionLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityReportSelection.Selection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-09 — Selecção por município sobre fixtures em memória. IBGEs
 * fictícios da base 12k (9990001/9990002), como nos testes dos irmãos.
 */
class MunicipalityReportSelectionTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String IBGE_A = "9990001";
    private static final String IBGE_B = "9990002";
    private static final LocalDate REF = LocalDate.of(2026, 10, 1);

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    private static BillLine bill(int n, String ibge, String total) {
        BigDecimal t = new BigDecimal(total);
        return new BillLine(TENANT, id(100_000 + n), id(200_000 + n), REF, ibge, t, t, new BigDecimal("0.00"));
    }

    private static ConsumptionLine consumption(int n, String ibge, int billed) {
        return new ConsumptionLine(TENANT, id(300_000 + n), id(200_000 + n), REF, "WATER", ibge, billed, billed);
    }

    private static List<BillLine> billsMistas() {
        return List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00"),
                bill(3, IBGE_B, "50.00"), bill(4, null, "7.50"));
    }

    private static List<ConsumptionLine> consumosMistos() {
        return List.of(consumption(1, IBGE_A, 10), consumption(2, IBGE_B, 12), consumption(3, null, 1));
    }

    @Test
    void relatorioSoDoMunicipioXUsaSoAParcelaX() {
        Selection s = MunicipalityReportSelection.selectFromLines(
                TENANT, REF, IBGE_B, billsMistas(), consumosMistos());

        // So a parcela B: 1 conta de 50,00 e 12 m3 — nenhuma linha de A
        // nem da parcela sem derivacao entra na seleccao.
        assertEquals(1, s.parcel().billCount());
        assertEquals(new BigDecimal("50.00"), s.parcel().billsTotal());
        assertEquals(1, s.parcel().consumptionRecordCount());
        assertEquals(12, s.parcel().billedConsumption());
        assertEquals(List.of(IBGE_A), s.excludedMunicipalities());
        assertEquals(IBGE_B, s.billsProvenance().municipalityIbge());
        assertEquals("bills_municipality_cut", s.billsProvenance().sourceView());
        assertEquals("consumption_records_municipality_cut", s.consumptionProvenance().sourceView());
    }

    @Test
    void asDuasViasDeSeleccaoDevolvemAMesmaParcela() {
        Closing closing = MunicipalityClosing.closeReference(TENANT, REF, billsMistas(), consumosMistos());
        Selection viaFecho = MunicipalityReportSelection.select(closing, IBGE_A);
        Selection viaLinhas = MunicipalityReportSelection.selectFromLines(
                TENANT, REF, IBGE_A, billsMistas(), consumosMistos());

        assertEquals(viaFecho.parcel(), viaLinhas.parcel());
        assertEquals(2, viaFecho.parcel().billCount());
        assertEquals(new BigDecimal("300.00"), viaFecho.parcel().billsTotal());
        assertEquals(List.of(IBGE_B), viaFecho.excludedMunicipalities());
    }

    @Test
    void seleccoesDosDoisMunicipiosParticionamOConsolidado() {
        Selection a = MunicipalityReportSelection.selectFromLines(
                TENANT, REF, IBGE_A, billsMistas(), consumosMistos());
        Selection b = MunicipalityReportSelection.selectFromLines(
                TENANT, REF, IBGE_B, billsMistas(), consumosMistos());
        var oficial = OfficialConsolidation.of(TENANT, REF, billsMistas(), consumosMistos());

        // A + B + sem derivacao (7,50 / 1 m3) = total oficial: a seleccao
        // nao cria nem perde linhas; o que nao e do municipio fica fora
        // da seleccao mas dentro do consolidado.
        BigDecimal soma = a.parcel().billsTotal().add(b.parcel().billsTotal())
                .add(oficial.unassignedParcel().orElseThrow().parcel().billsTotal());
        assertEquals(oficial.total().billsTotal(), soma);
        assertEquals(23, a.parcel().billedConsumption() + b.parcel().billedConsumption() + 1);
    }

    @Test
    void municipioUnicoSeleccaoIgualConsolidado() {
        List<BillLine> bills = List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00"));
        List<ConsumptionLine> cons = List.of(consumption(1, IBGE_A, 10));
        Selection s = MunicipalityReportSelection.selectFromLines(TENANT, REF, IBGE_A, bills, cons);
        var oficial = OfficialConsolidation.of(TENANT, REF, bills, cons);

        assertEquals(oficial.total().billCount(), s.parcel().billCount());
        assertEquals(oficial.total().billsTotal(), s.parcel().billsTotal());
        assertEquals(oficial.total().billedConsumption(), s.parcel().billedConsumption());
        assertTrue(s.excludedMunicipalities().isEmpty());
    }

    @Test
    void municipioAusenteEZeroDeclaradoEIbgeEmBrancoRecusado() {
        Selection s = MunicipalityReportSelection.selectFromLines(
                TENANT, REF, "9990003", billsMistas(), consumosMistos());

        assertTrue(s.isDeclaredZero());
        assertEquals(0, s.parcel().billCount());
        assertEquals(new BigDecimal("0.00"), s.parcel().billsTotal());
        assertEquals(List.of(IBGE_A, IBGE_B), s.excludedMunicipalities());

        assertThrows(IllegalArgumentException.class, () -> MunicipalityReportSelection
                .selectFromLines(TENANT, REF, "  ", billsMistas(), consumosMistos()));
    }

    @Test
    void catalogoDeRelatoriosDaSeleccaoEoDeclaradoDaV15_1() {
        // Evidencia da V15_1 (P3.22): views sobre bills, bill_items+bills
        // e consumption_records — listas disjuntas, 10 relatorios.
        assertEquals(List.of("RPT-006", "RPT-009", "RPT-010", "RPT-011", "RPT-017", "RPT-019"),
                MunicipalityReportSelection.BILLS_BASED_REPORTS);
        assertEquals(List.of("RPT-007", "RPT-016"),
                MunicipalityReportSelection.BILL_ITEMS_BASED_REPORTS);
        assertEquals(List.of("RPT-012", "RPT-014"),
                MunicipalityReportSelection.CONSUMPTION_BASED_REPORTS);
        assertEquals(10, MunicipalityReportSelection.selectableReportCodes().size());
    }
}

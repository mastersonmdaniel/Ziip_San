package br.com.ziipsan.commercial.billing.municipality;

import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.BillLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Closing;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.ConsumptionLine;
import br.com.ziipsan.commercial.billing.municipality.OfficialConsolidation.Official;
import br.com.ziipsan.commercial.billing.municipality.OfficialConsolidation.ParcelFigure;
import br.com.ziipsan.commercial.billing.municipality.OfficialConsolidation.Provenance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-09 — Consolidado oficial sobre fixtures em memória. Os IBGEs são
 * os códigos FICTÍCIOS da base fictícia 12k (9990001/9990002, lidos do
 * seed); a fixture das âncoras reproduz os TOTAIS provados do MM-04
 * (contas 8.475/2.825; R$ 914.529,00/R$ 306.502,00; tenant 11.300 /
 * R$ 1.221.031,00; consumos 146.134/49.416 m³, tenant 195.550 m³)
 * gerando as linhas programaticamente — a prova desses números contra
 * o seed é a diferencial PG, não este teste.
 */
class OfficialConsolidationTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-3333-4444-8555-666666666666");
    private static final String IBGE_A = "9990001"; // fictício (seed base-ficticia-12k)
    private static final String IBGE_B = "9990002"; // fictício (seed base-ficticia-12k)
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

    @Test
    void provenienciaCompletaPorParcela() {
        Official o = OfficialConsolidation.of(TENANT, REF,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_B, "50.00")),
                List.of(consumption(1, IBGE_A, 10), consumption(2, IBGE_B, 12)));

        assertEquals(2, o.parcels().size());
        for (ParcelFigure f : o.parcels()) {
            Provenance pb = f.billsProvenance();
            assertEquals(f.municipalityIbge(), pb.municipalityIbge());
            assertEquals(REF, pb.referenceMonth());
            assertEquals("bills_municipality_cut", pb.sourceView());
            assertTrue(pb.derivation().contains("consumer_unit_municipality"));

            Provenance pc = f.consumptionProvenance();
            assertEquals(f.municipalityIbge(), pc.municipalityIbge());
            assertEquals(REF, pc.referenceMonth());
            assertEquals("consumption_records_municipality_cut", pc.sourceView());
            assertTrue(pc.derivation().contains("consumer_unit_municipality"));
        }
        // A proveniencia do total e a lista das proveniencias das parcelas:
        // 2 parcelas x 2 fontes-view, na ordem das parcelas.
        assertEquals(4, o.totalProvenance().size());
        assertEquals(o.parcels().get(0).billsProvenance(), o.totalProvenance().get(0));
        assertEquals(o.parcels().get(0).consumptionProvenance(), o.totalProvenance().get(1));
        assertEquals(o.parcels().get(1).billsProvenance(), o.totalProvenance().get(2));
        assertEquals(o.parcels().get(1).consumptionProvenance(), o.totalProvenance().get(3));
    }

    @Test
    void consolidadoOficialReproduzAsAncorasDoMm04() {
        // Contas: esquema do MM-04 — (n-1) de 100,00 e a ultima com o resto.
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

        // Consumos: base 17 m3 por registo; o resto (+1 m3) nos primeiros
        // registos — A: 8475*17 = 144075, resto 2059 -> 146134;
        // B: 2825*17 = 48025, resto 1391 -> 49416.
        List<ConsumptionLine> cons = new ArrayList<>();
        int m = 0;
        for (int i = 0; i < 8475; i++) {
            cons.add(consumption(m++, IBGE_A, i < 2059 ? 18 : 17));
        }
        for (int i = 0; i < 2825; i++) {
            cons.add(consumption(m++, IBGE_B, i < 1391 ? 18 : 17));
        }

        Official o = OfficialConsolidation.of(TENANT, REF, bills, cons);

        ParcelFigure a = o.parcelFor(IBGE_A).orElseThrow();
        assertEquals(8475, a.parcel().billCount());
        assertEquals(new BigDecimal("914529.00"), a.parcel().billsTotal());
        assertEquals(8475, a.parcel().consumptionRecordCount());
        assertEquals(146134, a.parcel().billedConsumption());
        assertEquals(146134, a.parcel().calculatedConsumption());

        ParcelFigure b = o.parcelFor(IBGE_B).orElseThrow();
        assertEquals(2825, b.parcel().billCount());
        assertEquals(new BigDecimal("306502.00"), b.parcel().billsTotal());
        assertEquals(2825, b.parcel().consumptionRecordCount());
        assertEquals(49416, b.parcel().billedConsumption());

        // Total oficial = ancora do tenant (MM-04) e = soma das parcelas.
        assertEquals(11300, o.total().billCount());
        assertEquals(new BigDecimal("1221031.00"), o.total().billsTotal());
        assertEquals(11300, o.total().consumptionRecordCount());
        assertEquals(195550, o.total().billedConsumption());
        assertEquals(146134 + 49416, o.total().billedConsumption());
    }

    @Test
    void totalIgualSomaDasParcelasEIgoalAoTotalDirectoDasLinhas() {
        List<BillLine> bills = List.of(
                bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00"),
                bill(3, IBGE_B, "50.00"), bill(4, null, "7.50"));
        List<ConsumptionLine> cons = List.of(
                consumption(1, IBGE_A, 10), consumption(2, IBGE_B, 12), consumption(3, null, 1));
        Official o = OfficialConsolidation.of(TENANT, REF, bills, cons);

        BigDecimal somaParcelas = o.parcels().stream().map(f -> f.parcel().billsTotal())
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        BigDecimal directo = bills.stream().map(BillLine::totalValue)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        assertEquals(directo, o.total().billsTotal());
        assertEquals(somaParcelas, o.total().billsTotal());
        assertEquals(23, o.total().billedConsumption());
        // A parcela sem derivacao existe no oficial, com proveniencia de IBGE nulo declarado.
        ParcelFigure un = o.unassignedParcel().orElseThrow();
        assertNull(un.billsProvenance().municipalityIbge());
        assertEquals(REF, un.billsProvenance().referenceMonth());
    }

    @Test
    void municipioUnicoIgualConsolidado() {
        Official o = OfficialConsolidation.of(TENANT, REF,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_A, "200.00")),
                List.of(consumption(1, IBGE_A, 10)));

        assertEquals(1, o.parcels().size());
        var only = o.parcels().get(0).parcel();
        assertEquals(only.billCount(), o.total().billCount());
        assertEquals(only.billsTotal(), o.total().billsTotal());
        assertEquals(only.waterValue(), o.total().waterValue());
        assertEquals(only.sewerValue(), o.total().sewerValue());
        assertEquals(only.consumptionRecordCount(), o.total().consumptionRecordCount());
        assertEquals(only.billedConsumption(), o.total().billedConsumption());
        assertEquals(only.calculatedConsumption(), o.total().calculatedConsumption());
        assertEquals(2, o.totalProvenance().size());
    }

    @Test
    void vazioEZeroDeclarado() {
        Official o = OfficialConsolidation.of(TENANT, REF, List.of(), List.of());

        assertTrue(o.parcels().isEmpty());
        assertTrue(o.totalProvenance().isEmpty());
        assertTrue(o.isDeclaredZero());
        assertEquals(0, o.total().billCount());
        assertEquals(new BigDecimal("0.00"), o.total().billsTotal());
        assertEquals(0, o.total().billedConsumption());
        assertEquals(TENANT, o.tenantId());
        assertEquals(REF, o.referenceMonth());
    }

    @Test
    void fechoSemReferenciaELinhaDeOutroTenantSaoRecusados() {
        Closing semRef = MunicipalityClosing.close(TENANT,
                List.of(bill(1, IBGE_A, "100.00")), List.of());
        assertThrows(IllegalArgumentException.class, () -> OfficialConsolidation.wrap(semRef));

        BillLine alheia = new BillLine(OTHER_TENANT, id(900_001), id(900_002), REF, IBGE_A,
                new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("0.00"));
        assertThrows(IllegalArgumentException.class,
                () -> OfficialConsolidation.of(TENANT, REF, List.of(alheia), List.of()));
    }

    @Test
    void alinhamentoComEd77PelaChaveIbgeDeclarada() {
        Official o = OfficialConsolidation.of(TENANT, REF,
                List.of(bill(1, IBGE_A, "100.00"), bill(2, IBGE_B, "50.00"), bill(3, null, "7.50")),
                List.of());

        // A chave de juncao com a ED-77 e o proprio IBGE da parcela.
        assertEquals(IBGE_A, o.parcelFor(IBGE_A).orElseThrow().ed77MunicipalityCode());
        assertEquals(IBGE_B, o.parcelFor(IBGE_B).orElseThrow().ed77MunicipalityCode());
        assertTrue(o.alignsWithEd77(IBGE_A));
        assertTrue(o.alignsWithEd77(IBGE_B));
        assertFalse(o.alignsWithEd77("4205407")); // IBGE real qualquer, ausente do fecho ficticio
        // Sem derivacao: sem codigo ED-77 — declarado, nao inventado.
        assertNull(o.unassignedParcel().orElseThrow().ed77MunicipalityCode());
    }
}

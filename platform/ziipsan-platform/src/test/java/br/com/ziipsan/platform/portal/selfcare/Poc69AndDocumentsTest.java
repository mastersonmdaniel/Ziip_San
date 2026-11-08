package br.com.ziipsan.platform.portal.selfcare;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Poc69AndDocumentsTest {
    static Poc69SelfCareService.BillSummary bill(YearMonth ref, Poc69SelfCareService.BillStatus st) {
        return new Poc69SelfCareService.BillSummary(UUID.randomUUID(), ref, ref.atDay(10),
                new BigDecimal("100.00"), st);
    }
    static Poc69SelfCareService service(List<Poc69SelfCareService.BillSummary> bills) {
        return new Poc69SelfCareService(new Poc69SelfCareService.BillsPort() {
            public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return "dono".equals(s); }
            public List<Poc69SelfCareService.BillSummary> bills(UUID t, UUID u, YearMonth f, YearMonth to) { return bills; }
            public boolean billBelongsToUnit(UUID t, UUID u, UUID b) {
                return bills.stream().anyMatch(x -> x.billId().equals(b)); }
            public Optional<String> secondCopyDocumentRef(UUID t, UUID b) {
                return billBelongsToUnit(t, null, b) ? Optional.of("doc-" + b) : Optional.empty(); }
        });
    }
    @Test void pendentesEQuitadasDoUltimoAno() {
        var svc = service(List.of(bill(YearMonth.of(2026, 10), Poc69SelfCareService.BillStatus.PENDING),
                bill(YearMonth.of(2026, 9), Poc69SelfCareService.BillStatus.SETTLED),
                bill(YearMonth.of(2025, 1), Poc69SelfCareService.BillStatus.SETTLED)));
        var now = YearMonth.of(2026, 10);
        assertEquals(2, svc.lastYearBills(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT, now).size());
        assertEquals(1, svc.pending(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT, now).size());
        assertEquals(1, svc.settled(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT, now).size());
        assertThrows(IllegalStateException.class,
                () -> svc.lastYearBills(Ed38TestSupport.TENANT, "outro", Ed38TestSupport.UNIT, now));
    }
    @Test void segundaViaPendenteEQuitada() {
        var p = bill(YearMonth.of(2026, 10), Poc69SelfCareService.BillStatus.PENDING);
        var s = bill(YearMonth.of(2026, 9), Poc69SelfCareService.BillStatus.SETTLED);
        var svc = service(List.of(p, s));
        assertTrue(svc.secondCopy(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT, p.billId()).startsWith("doc-"));
        assertTrue(svc.secondCopy(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT, s.billId()).startsWith("doc-"));
    }
    @Test void simulacoesDeterministicas() {
        var tariff = new Poc69SelfCareService.TariffStructure(new BigDecimal("20.00"),
                List.of(new Poc69SelfCareService.TariffBand(new BigDecimal("10"), new BigDecimal("2.00")),
                        new Poc69SelfCareService.TariffBand(new BigDecimal("20"), new BigDecimal("4.00"))),
                new BigDecimal("80"));
        var sim = Poc69SelfCareService.simulateBill(new BigDecimal("15"), tariff);
        // 20 min + 10*2 + 5*4 = 60 agua; esgoto 80% = 48; total 108
        assertEquals(new BigDecimal("108.00"), sim.totalAmount());
        assertTrue(sim.simulationOnly());
        var parc = Poc69SelfCareService.simulateInstallments(new BigDecimal("100.00"), 3, BigDecimal.ZERO);
        assertEquals(new BigDecimal("100.00"),
                parc.amounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(parc.simulationOnly());
    }

    @Test void simulacaoParcelamentoJurosSimplesComTaxaPositiva() {
        // Correcao D3: regime SIMPLES declarado — 1000 a 2% a.m. em 4x:
        // total = 1000 x (1 + 0,02 x 4) = 1080,00 (o composto daria 1082,43).
        var parc = Poc69SelfCareService.simulateInstallments(new BigDecimal("1000.00"), 4, new BigDecimal("2"));
        assertEquals(List.of(new BigDecimal("270.00"), new BigDecimal("270.00"),
                        new BigDecimal("270.00"), new BigDecimal("270.00")), parc.amounts());
        assertEquals(new BigDecimal("1080.00"),
                parc.amounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(parc.simulationOnly());
        // 1200 a 1,5% a.m. em 3x: total = 1200 x (1 + 0,015 x 3) = 1254,00.
        var parc2 = Poc69SelfCareService.simulateInstallments(new BigDecimal("1200.00"), 3, new BigDecimal("1.5"));
        assertEquals(new BigDecimal("1254.00"),
                parc2.amounts().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static AnnualSettlementCertificate.YearBillsPort yearPort(List<AnnualSettlementCertificate.YearBill> bills) {
        return new AnnualSettlementCertificate.YearBillsPort() {
            public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            public List<AnnualSettlementCertificate.YearBill> billsOfYear(UUID t, UUID u, Year y) { return bills; }
        };
    }
    /** Os 12 meses de 2025; {@code openMonth} (1-12) fica nao quitado, ou 0 = todos quitados. */
    static List<AnnualSettlementCertificate.YearBill> fullYear2025(int openMonth) {
        var list = new java.util.ArrayList<AnnualSettlementCertificate.YearBill>();
        for (int m = 1; m <= 12; m++) {
            list.add(new AnnualSettlementCertificate.YearBill(YearMonth.of(2025, m), m != openMonth));
        }
        return list;
    }
    @Test void certidaoAnualSoComTudoQuitadoECodigoVerificador() {
        var docs = new AuthenticDocument(Ed38TestSupport.authenticity());
        var ok = new AnnualSettlementCertificate(yearPort(fullYear2025(0)), docs);
        var issued = ok.issue(Ed38TestSupport.TENANT, "dono", Ed38TestSupport.UNIT,
                Year.of(2025), "CERT-2025-1", LocalDate.of(2026, 1, 15));
        assertEquals("ADVANCED", issued.signatureLevel());
        assertTrue(docs.verifies("ANNUAL_SETTLEMENT_CERTIFICATE", "CERT-2025-1",
                LocalDate.of(2026, 1, 15), issued.verificationCode()));
        assertFalse(docs.verifies("ANNUAL_SETTLEMENT_CERTIFICATE", "CERT-2025-1",
                LocalDate.of(2026, 1, 16), issued.verificationCode()));
        assertTrue(issued.qrPayload().contains(issued.verificationCode()));
    }
    @Test void certidaoAnualRecusaMesFaltante() {
        // Correcao D1: ano com apenas 11 dos 12 meses, todos quitados, recusa.
        var docs = new AuthenticDocument(Ed38TestSupport.authenticity());
        var missingJuly = fullYear2025(0);
        missingJuly.removeIf(b -> b.reference().equals(YearMonth.of(2025, 7)));
        var svc = new AnnualSettlementCertificate(yearPort(missingJuly), docs);
        var ex = assertThrows(IllegalStateException.class, () -> svc.issue(Ed38TestSupport.TENANT, "dono",
                Ed38TestSupport.UNIT, Year.of(2025), "CERT-X", LocalDate.of(2026, 1, 15)));
        assertTrue(ex.getMessage().contains("2025-07"));
        // Caso extremo do defeito original: so jan-fev quitados tambem recusa.
        var janFeb = new AnnualSettlementCertificate(yearPort(List.of(
                new AnnualSettlementCertificate.YearBill(YearMonth.of(2025, 1), true),
                new AnnualSettlementCertificate.YearBill(YearMonth.of(2025, 2), true))), docs);
        assertThrows(IllegalStateException.class, () -> janFeb.issue(Ed38TestSupport.TENANT, "dono",
                Ed38TestSupport.UNIT, Year.of(2025), "CERT-Y", LocalDate.of(2026, 1, 15)));
    }
    @Test void certidaoAnualRecusaMesAberto() {
        // Correcao D1: os 12 meses presentes, um nao quitado, recusa.
        var docs = new AuthenticDocument(Ed38TestSupport.authenticity());
        var open = new AnnualSettlementCertificate(yearPort(fullYear2025(3)), docs);
        assertThrows(IllegalStateException.class, () -> open.issue(Ed38TestSupport.TENANT, "dono",
                Ed38TestSupport.UNIT, Year.of(2025), "CERT-Z", LocalDate.of(2026, 1, 15)));
    }
}

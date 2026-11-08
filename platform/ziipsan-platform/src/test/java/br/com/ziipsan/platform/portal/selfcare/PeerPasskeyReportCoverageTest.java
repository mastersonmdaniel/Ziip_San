package br.com.ziipsan.platform.portal.selfcare;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerPasskeyReportCoverageTest {
    static List<BigDecimal> cohort(int n, String v) {
        List<BigDecimal> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new BigDecimal(v));
        return l;
    }
    @Test void comparativoDesligadoPorPadraoNadaPublica() {
        var p = new Ed38TestSupport.Params();
        var pc = new PeerComparison(p);
        var r = pc.compare(Ed38TestSupport.TENANT, new BigDecimal("10"), cohort(10, "12"));
        assertFalse(r.published());
        assertEquals("DISABLED", r.position());
    }
    @Test void agregadoMinimoPiso5ClasseC() {
        var p = new Ed38TestSupport.Params(); p.peer = true;
        var pc = new PeerComparison(p);
        assertFalse(pc.compare(Ed38TestSupport.TENANT, new BigDecimal("10"), cohort(4, "12")).published());
        assertTrue(pc.compare(Ed38TestSupport.TENANT, new BigDecimal("10"), cohort(5, "12")).published());
        p.peerMin = 3;
        assertThrows(IllegalStateException.class, () -> pc.effectiveMinUnits(Ed38TestSupport.TENANT));
        p.peerMin = 8;
        assertFalse(pc.compare(Ed38TestSupport.TENANT, new BigDecimal("10"), cohort(7, "12")).published());
        assertTrue(pc.compare(Ed38TestSupport.TENANT, new BigDecimal("10"), cohort(8, "12")).published());
    }
    @Test void passkeyPelaPoliticaEd06() {
        var p = new Ed38TestSupport.Params();
        var pk = new PasskeyPolicy(p);
        var cred = new PasskeyPolicy.Credential(true, true);
        assertFalse(pk.enrollmentAllowed(Ed38TestSupport.TENANT));
        assertThrows(IllegalStateException.class, () -> pk.assertUsable(Ed38TestSupport.TENANT, cred, PasskeyPolicy.Use.LOGIN));
        p.passkey = true;
        assertTrue(pk.enrollmentAllowed(Ed38TestSupport.TENANT));
        pk.assertUsable(Ed38TestSupport.TENANT, cred, PasskeyPolicy.Use.MFA);
        assertTrue(pk.countsAsMfa(cred));
        assertThrows(IllegalStateException.class,
                () -> pk.assertUsable(Ed38TestSupport.TENANT, new PasskeyPolicy.Credential(false, true), PasskeyPolicy.Use.LOGIN));
    }
    @Test void relatoSemLoginAgrupaECriaProtocoloLocationOnly() {
        List<UUID> created = new ArrayList<>();
        Map<String, UUID> open = new HashMap<>();
        var svc = new AnonymousReportService(origin -> true, new AnonymousReportService.ProtocolPort() {
            public UUID createLocationOnlyProtocol(UUID t, String key, String loc, String desc, LocalDateTime at) {
                UUID id = UUID.randomUUID(); created.add(id); open.put(key, id); return id; }
            public Optional<UUID> openProtocolForEvent(UUID t, String key, LocalDateTime since) {
                return Optional.ofNullable(open.get(key)); }
            public void attachReport(UUID t, UUID pid, String photo, String contact) { }
        }, null);
        var now = LocalDateTime.of(2026, 11, 8, 10, 0);
        var r1 = new AnonymousReportService.Report(AnonymousReportService.Category.LEAK,
                "Rua A, 100", "foto1", null, "vazamento na calcada");
        var r2 = new AnonymousReportService.Report(AnonymousReportService.Category.LEAK,
                " rua a, 100 ", "foto2", "cidadao@exemplo", "mesmo vazamento");
        var o1 = svc.submit(Ed38TestSupport.TENANT, "ip1", r1, now, Duration.ofHours(24));
        var o2 = svc.submit(Ed38TestSupport.TENANT, "ip2", r2, now.plusHours(2), Duration.ofHours(24));
        assertFalse(o1.groupedIntoExisting());
        assertTrue(o2.groupedIntoExisting());
        assertEquals(o1.protocolId(), o2.protocolId());
        assertEquals(1, created.size());
        assertFalse(o1.networkOccurrenceCreated()); // ED-92 nao existe: declarado, nao fingido
    }
    @Test void relatoRecusadoPelaDefesaP33() {
        var svc = new AnonymousReportService(origin -> false, new AnonymousReportService.ProtocolPort() {
            public UUID createLocationOnlyProtocol(UUID t, String k, String l, String d, LocalDateTime a) {
                throw new AssertionError("nao deveria criar"); }
            public Optional<UUID> openProtocolForEvent(UUID t, String k, LocalDateTime s) { return Optional.empty(); }
            public void attachReport(UUID t, UUID p, String ph, String c) { }
        }, null);
        var r = new AnonymousReportService.Report(AnonymousReportService.Category.NO_WATER,
                "Bairro B", "foto-sem-agua", null, "sem agua");
        assertThrows(IllegalStateException.class,
                () -> svc.submit(Ed38TestSupport.TENANT, "bot", r, LocalDateTime.now(), Duration.ofHours(1)));
    }
    @Test void relatoExigeFotoEContatoEOpcional() {
        // Correcao D6: no acceptance, o opcional e o CONTATO; a foto e exigida.
        assertThrows(IllegalArgumentException.class, () -> new AnonymousReportService.Report(
                AnonymousReportService.Category.LEAK, "Rua A, 100", null, null, "vazamento"));
        assertThrows(IllegalArgumentException.class, () -> new AnonymousReportService.Report(
                AnonymousReportService.Category.LEAK, "Rua A, 100", "  ", "cidadao@exemplo", "vazamento"));
        // Com foto e SEM contato, o relato e valido.
        var ok = new AnonymousReportService.Report(AnonymousReportService.Category.LEAK,
                "Rua A, 100", "foto1", null, "vazamento");
        assertEquals("foto1", ok.photoRef());
        assertNull(ok.contactOrNull());
    }
    @Test void samae220Exige90EAxeLimpo() {
        var items = new ArrayList<>(Collections.nCopies(9, true)); items.add(false);
        var r = PortalCoverage.samae220(items);
        assertEquals(90, r.percent()); assertTrue(r.passed());
        assertTrue(PortalCoverage.releaseGate(new PortalCoverage.AxeSummary(0, 0), r));
        assertFalse(PortalCoverage.releaseGate(new PortalCoverage.AxeSummary(1, 0), r));
        var below = PortalCoverage.samae220(List.of(true, true, true, true, true, true, true, true, false, false));
        assertFalse(below.passed());
    }
    @Test void agendamentoSoEmJanelaOferecidaDaEd36() {
        var offer = new SelfCareScheduling.WindowOffer("W1", LocalDateTime.of(2026, 11, 10, 8, 0), 2);
        var sched = new SelfCareScheduling(new SelfCareScheduling.SchedulingPort() {
            public List<SelfCareScheduling.WindowOffer> offeredWindows(UUID t, UUID os) { return List.of(offer); }
            public String book(UUID t, UUID os, String w, LocalDateTime s) { return "bk-1"; }
            public String confirm(UUID t, String ref) { return ref + ":confirmed"; }
        });
        UUID os = UUID.randomUUID();
        assertEquals("bk-1", sched.book(Ed38TestSupport.TENANT, os, "W1", offer.start()));
        assertThrows(IllegalStateException.class,
                () -> sched.book(Ed38TestSupport.TENANT, os, "W9", offer.start()));
    }
}

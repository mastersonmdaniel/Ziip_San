package br.com.ziipsan.core.regulatory.agenda;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** GES-05: geracao do calendario — natureza juridica, perfil, catalogo da P3.15, ancoras do aceite. */
class CalendarGenerationTest {

    private ObligationCalendarService service(AgendaPorts.NormCatalogPort port,
                                              AgendaFixtures.RecordingReminders rem) {
        return new ObligationCalendarService(port, rem);
    }

    @Test void autarquiaGeneratesAllApplicableObligations() {
        var gen = service(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027);
        Set<ObligationKind> kinds = gen.items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        assertEquals(Set.of(ObligationKind.SINISA_ANNUAL_CYCLE, ObligationKind.SOCIAL_TARIFF_ANNUAL_REPORT,
                ObligationKind.ANNUAL_DISCHARGE_DECLARATION, ObligationKind.WATER_LOSS_BALANCE_NR15,
                ObligationKind.ESFINGE_BIMONTHLY, ObligationKind.SISAGUA,
                ObligationKind.TARIFF_READJUSTMENT_PUBLICATION, ObligationKind.WASTE_MTR_CDF_DMR_MANUAL), kinds);
        assertEquals(6, gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).size(), "e-Sfinge e bimestral: 6 remessas");
        assertTrue(gen.unresolvedKinds().isEmpty());
        assertTrue(gen.items().stream().allMatch(i -> AgendaFixtures.RESP.equals(i.responsibleUserId())));
        assertTrue(gen.items().stream().allMatch(i -> i.normCatalogKey() != null),
                "cada item nasce da norma lida do catalogo da P3.15");
    }

    @Test void twoTenantsWithDifferentLegalNaturesGenerateDifferentCalendars() {
        // Correcao D3 (REVISAO-ED-98): tenants DISTINTOS por natureza e
        // a MESMA flag fiscal nos tres — a diferenca de calendario e
        // atribuivel a NATUREZA, nao a flag (a versao anterior comparava
        // autarquia sem flag x ente com flag, e ente/concessionaria
        // reusavam o mesmo TENANT_B: a perna autarquia x ente provava a
        // flag, nao a natureza — sonda S3a do revisor).
        var svc = service(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders());
        var autarquia = svc.generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, true), 2027);
        var ente = svc.generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_B, TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE, true), 2027);
        var concessionaria = svc.generate(AgendaFixtures.profile(
                AgendaFixtures.TENANT_C, TenantLegalNature.CONCESSIONARIA_PRIVADA, true), 2027);
        Set<ObligationKind> kAut = autarquia.items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        Set<ObligationKind> kEnte = ente.items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        Set<ObligationKind> kConc = concessionaria.items().stream().map(AgendaItem::kind).collect(Collectors.toSet());
        assertNotEquals(kAut, kEnte, "naturezas diferentes geram calendarios diferentes (GES-05)");
        assertTrue(kEnte.contains(ObligationKind.RREO) && kEnte.contains(ObligationKind.RGF));
        assertEquals(6, ente.itemsOf(ObligationKind.RREO).size(), "RREO bimestral");
        assertEquals(3, ente.itemsOf(ObligationKind.RGF).size(), "RGF quadrimestral");
        // D2: com a flag IGUALMENTE ligada, a autarquia NAO recebe
        // RREO/RGF — sao do ente (natureza), nao da flag.
        assertFalse(kAut.contains(ObligationKind.RREO),
                "mesma flag ligada: RREO/RGF sao do ente (natureza), nao da flag");
        assertFalse(kAut.contains(ObligationKind.RGF));
        assertTrue(kAut.contains(ObligationKind.ESFINGE_BIMONTHLY), "autarquia e jurisdicionada ao TC");
        assertTrue(kEnte.contains(ObligationKind.ESFINGE_BIMONTHLY), "ente e jurisdicionado ao TC");
        assertFalse(kConc.contains(ObligationKind.ESFINGE_BIMONTHLY),
                "concessionaria privada nao e jurisdicionada direta do e-Sfinge");
        assertFalse(kConc.contains(ObligationKind.RREO) || kConc.contains(ObligationKind.RGF),
                "concessionaria privada nao emite RREO/RGF nem com a flag ligada (D2)");
        assertNotEquals(kAut, kConc);
        assertNotEquals(kEnte, kConc);
    }

    @Test void ruleWhoseNormIsMissingFromCatalogIsUnresolvedNotInvented() {
        var partial = AgendaFixtures.portOf(new br.com.ziipsan.platform.central.complete.RegulatoryLibrary(
                AgendaFixtures.fullCatalog().stream()
                        .filter(n -> !n.catalogKey().equals("sisagua-vigilancia")).toList()));
        var gen = service(partial, new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027);
        assertEquals(List.of(ObligationKind.SISAGUA), gen.unresolvedKinds());
        assertTrue(gen.itemsOf(ObligationKind.SISAGUA).isEmpty());
    }

    @Test void dischargeDeclarationAnchoredInMayWithDayUnDefined() {
        var gen = service(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027);
        AgendaItem item = gen.itemsOf(ObligationKind.ANNUAL_DISCHARGE_DECLARATION).get(0);
        assertNull(item.dueDate(), "dia sem fonte nao vira data fingida");
        assertTrue(item.dueLabel().contains("maio/2027"));
        assertTrue(item.dueLabel().contains("[A DEFINIR]"));
    }

    @Test void readjustmentPublicationIs30DaysBeforeBaseDate() {
        var gen = service(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders())
                .generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027);
        AgendaItem item = gen.itemsOf(ObligationKind.TARIFF_READJUSTMENT_PUBLICATION).get(0);
        assertEquals(LocalDate.of(2027, 6, 1), item.dueDate(), "data-base 01/07 menos 30 dias do aceite");
    }

    @Test void generationRefusesItemWithoutResponsible() {
        var noResp = new TenantObligationProfile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA,
                null, false, null, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () ->
                service(AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders()).generate(noResp, 2027));
    }

    @Test void remindersScheduledForEveryItemThroughEd07Port() {
        var rem = new AgendaFixtures.RecordingReminders();
        var svc = service(AgendaFixtures.fullPort(), rem);
        var gen = svc.generate(AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false), 2027);
        var withReminders = svc.scheduleReminders(gen);
        assertEquals(gen.items().size(), rem.requests.size());
        assertTrue(withReminders.stream().allMatch(i -> i.reminderRef() != null
                && i.reminderRef().startsWith("ED07-ALERT:")));
        assertTrue(rem.requests.stream().allMatch(r -> AgendaFixtures.RESP.equals(r.responsibleUserId())));
    }
}
